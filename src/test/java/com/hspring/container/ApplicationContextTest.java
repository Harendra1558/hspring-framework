package com.hspring.container;

import com.hspring.testbeans.config.Greeter;
import com.hspring.testbeans.config.Greeting;
import com.hspring.testbeans.fieldcycle.PingService;
import com.hspring.testbeans.fieldcycle.PongService;
import com.hspring.testbeans.ok.EnglishGreetingService;
import com.hspring.testbeans.ok.GreetingService;
import com.hspring.testbeans.ok.HelloController;
import com.hspring.testbeans.ok.OrderService;
import com.hspring.testbeans.ok.Repository;
import com.project.MultiThreadedWebServer.core.ApplicationContext;
import com.project.MultiThreadedWebServer.core.BeanCreationException;
import com.project.MultiThreadedWebServer.core.PropertiesLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the IoC container against small fixture packages under com.hspring.testbeans.
 * Each package is scanned on its own, so each scenario is isolated.
 */
class ApplicationContextTest {

    @Test
    void createsBeansAndInjectsThroughConstructorFieldsAndInterfaces() {
        ApplicationContext context = new ApplicationContext("com.hspring.testbeans.ok");

        OrderService orderService = context.getBean(OrderService.class);
        Repository repository = context.getBean(Repository.class);
        assertNotNull(orderService);

        // Constructor injection, and beans are singletons (same instance everywhere)
        assertSame(repository, orderService.getRepository());

        // Injection by interface type resolves to the single implementation
        assertInstanceOf(EnglishGreetingService.class, orderService.getGreetingService());
        assertSame(context.getBean(GreetingService.class), orderService.getGreetingService());

        // Controllers are tracked for route registration
        assertEquals(1, context.getControllers().size());
        assertInstanceOf(HelloController.class, context.getControllers().get(0));
    }

    @Test
    void injectsFieldsIncludingInheritedOnes() throws Exception {
        ApplicationContext context = new ApplicationContext("com.hspring.testbeans.ok");
        HelloController controller = context.getBean(HelloController.class);

        assertSame(context.getBean(OrderService.class), field(controller, "orderService"));
        // greetingService is declared in the abstract superclass BaseController
        assertSame(context.getBean(GreetingService.class), field(controller, "greetingService"));
        // optional dependency with no matching bean stays null instead of failing
        assertNull(field(controller, "optionalDependency"));
    }

    @Test
    void autowiredConstructorWinsOverNoArgConstructor() throws Exception {
        ApplicationContext context = new ApplicationContext("com.hspring.testbeans.ok");
        Object bean = context.getBean("multiConstructorBean");
        assertSame(context.getBean(Repository.class), field(bean, "repository"));
    }

    @Test
    void beanNamesUseAnnotationValueOrLowerCamelCase() {
        ApplicationContext context = new ApplicationContext("com.hspring.testbeans.ok");
        assertInstanceOf(EnglishGreetingService.class, context.getBean("greeter"));
        assertInstanceOf(OrderService.class, context.getBean("orderService"));
        assertNull(context.getBean("doesNotExist"));
    }

    @Test
    void callsPostConstructAfterInjectionAndPreDestroyOnClose() {
        OrderService.EVENTS.clear();
        ApplicationContext context = new ApplicationContext("com.hspring.testbeans.ok");
        assertEquals(List.of("postConstruct:true"), OrderService.EVENTS);

        context.close();
        assertEquals(List.of("postConstruct:true", "preDestroy"), OrderService.EVENTS);
        assertNull(context.getBean(OrderService.class), "context is empty after close()");
    }

    @Test
    void circularConstructorDependencyFailsFastWithClearMessage() {
        BeanCreationException e = assertThrows(BeanCreationException.class,
                () -> new ApplicationContext("com.hspring.testbeans.cycle"));
        assertTrue(e.getMessage().contains("Circular dependency"), e.getMessage());
        assertTrue(e.getMessage().contains("ChickenService"), e.getMessage());
        assertTrue(e.getMessage().contains("EggService"), e.getMessage());
    }

    @Test
    void circularFieldDependencyIsResolved() {
        ApplicationContext context = new ApplicationContext("com.hspring.testbeans.fieldcycle");
        PingService ping = context.getBean(PingService.class);
        PongService pong = context.getBean(PongService.class);
        assertSame(pong, ping.pong);
        assertSame(ping, pong.ping);
    }

    @Test
    void missingRequiredDependencyFails() {
        BeanCreationException e = assertThrows(BeanCreationException.class,
                () -> new ApplicationContext("com.hspring.testbeans.missing"));
        assertTrue(e.getMessage().contains("NotABean"), e.getMessage());
    }

    @Test
    void ambiguousDependencyFails() {
        BeanCreationException e = assertThrows(BeanCreationException.class,
                () -> new ApplicationContext("com.hspring.testbeans.ambiguous"));
        assertTrue(e.getMessage().contains("Notifier"), e.getMessage());
    }

    @Test
    void scanningAnUnknownPackageYieldsEmptyContext() {
        ApplicationContext context = new ApplicationContext("com.hspring.does.not.exist");
        assertEquals(0, context.getBeanCount());
    }

    @Test
    void configurationBeansValuesAndQualifiers() {
        PropertiesLoader properties = new PropertiesLoader(Map.of("test.repeat", "3", "test.enabled", "true"));
        ApplicationContext context = new ApplicationContext(properties, "com.hspring.testbeans.config");
        Greeter greeter = context.getBean(Greeter.class);

        // @Bean method parameters are resolved from the container (Punctuation component)
        assertEquals(new Greeting("hello!"), greeter.getGreeting());
        // @Qualifier picks between two @Bean methods of the same type
        assertSame(context.getBean("greeting"), greeter.getGreeting());
        assertSame(context.getBean("loudGreeting"), greeter.getLoudGreeting());
        // @Value converts property strings to the field type
        assertEquals(3, greeter.getRepeat());
        assertTrue(greeter.isEnabled());
        // Two beans of type Greeting: a by-type lookup is ambiguous
        assertThrows(BeanCreationException.class, () -> context.getBean(Greeting.class));
    }

    @Test
    void missingValuePropertyFails() {
        BeanCreationException e = assertThrows(BeanCreationException.class,
                () -> new ApplicationContext("com.hspring.testbeans.missingvalue"));
        assertTrue(e.getMessage().contains("test.not.defined"), e.getMessage());
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                // look in the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }
}
