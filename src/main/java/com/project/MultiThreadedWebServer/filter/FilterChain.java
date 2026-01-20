package com.project.MultiThreadedWebServer.filter;

import com.project.MultiThreadedWebServer.core.HttpRequest;
import com.project.MultiThreadedWebServer.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FilterChain manages the execution of multiple filters in order.
 * Similar to Spring's FilterChain.
 * 
 * The chain executes filters in order:
 * 1. preHandle of Filter 1
 * 2. preHandle of Filter 2
 * 3. ... (all preHandles)
 * 4. Controller execution
 * 5. postHandle of Filter N (reverse order)
 * 6. postHandle of Filter N-1
 * 7. ... (all postHandles in reverse)
 */
public class FilterChain {

    private static final Logger logger = LoggerFactory.getLogger(FilterChain.class);

    private final List<Filter> filters = new ArrayList<>();

    /**
     * Adds a filter to the chain.
     * 
     * @param filter The filter to add
     */
    public void addFilter(Filter filter) {
        filters.add(filter);
        // Keep filters sorted by order
        filters.sort(Comparator.comparingInt(Filter::getOrder));
        logger.info("Added filter: {} with order {}", filter.getClass().getSimpleName(), filter.getOrder());
    }

    /**
     * Executes all filter preHandle methods.
     * 
     * @param request  The HTTP request
     * @param response The HTTP response
     * @return true if all filters passed, false if any filter short-circuited
     */
    public boolean applyPreHandle(HttpRequest request, HttpResponse response) {
        for (Filter filter : filters) {
            try {
                if (!filter.preHandle(request, response)) {
                    logger.debug("Request short-circuited by filter: {}", filter.getClass().getSimpleName());
                    return false;
                }
            } catch (Exception e) {
                logger.error("Error in filter preHandle: {}", filter.getClass().getSimpleName(), e);
                return false;
            }
        }
        return true;
    }

    /**
     * Executes all filter postHandle methods in reverse order.
     * 
     * @param request  The HTTP request
     * @param response The HTTP response
     */
    public void applyPostHandle(HttpRequest request, HttpResponse response) {
        // Execute in reverse order
        for (int i = filters.size() - 1; i >= 0; i--) {
            try {
                filters.get(i).postHandle(request, response);
            } catch (Exception e) {
                logger.error("Error in filter postHandle: {}", filters.get(i).getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Executes all filter afterCompletion methods in reverse order.
     * 
     * @param request   The HTTP request
     * @param response  The HTTP response
     * @param exception Any exception that occurred, or null
     */
    public void applyAfterCompletion(HttpRequest request, HttpResponse response, Exception exception) {
        // Execute in reverse order
        for (int i = filters.size() - 1; i >= 0; i--) {
            try {
                filters.get(i).afterCompletion(request, response, exception);
            } catch (Exception e) {
                logger.error("Error in filter afterCompletion: {}", filters.get(i).getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Returns the number of filters in the chain.
     */
    public int size() {
        return filters.size();
    }
}
