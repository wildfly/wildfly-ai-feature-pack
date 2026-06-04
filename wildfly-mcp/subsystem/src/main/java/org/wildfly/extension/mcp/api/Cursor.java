/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.StreamSupport;

/**
 * Opaque cursor for MCP pagination.
 * Encodes the name of the last item returned in a page so the next request
 * can resume from the following item.
 *
 * @see <a href="https://spec.modelcontextprotocol.io/specification/2024-11-05/server/utilities/pagination/">MCP Pagination spec</a>
 */
public final class Cursor {

    private Cursor() {
    }

    /**
     * Encodes the name of the last item in a page into an opaque, base64-encoded cursor string.
     * <p>
     * The cursor is used by clients to request the next page of results. The encoding ensures
     * that the cursor is URL-safe and does not expose internal implementation details.
     * </p>
     *
     * @param lastItemName the name of the last item in the current page
     * @return a base64-encoded cursor string representing the pagination position
     * @see #decode(String) to reverse this operation
     */
    public static String encode(String lastItemName) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(lastItemName.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a base64-encoded cursor string back to the last item name from the previous page.
     * <p>
     * This method reverses the {@link #encode(String)} operation, extracting the item name
     * that was used to create the cursor. The decoded name is then used to determine where
     * to resume pagination in the sorted list of items.
     * </p>
     *
     * @param cursorValue the base64-encoded cursor string from a previous page
     * @return the name of the last item from the previous page
     * @throws IllegalArgumentException if the cursor is not valid base64
     * @see #encode(String) to create a cursor
     */
    public static String decode(String cursorValue) {
        return new String(Base64.getUrlDecoder().decode(cursorValue), StandardCharsets.UTF_8);
    }

    /**
     * Extracts a page of items from a sorted list based on a cursor and page size.
     * <p>
     * If {@code cursorValue} is null, returns the first page. Otherwise, decodes the cursor
     * to find the last item from the previous page and returns items starting from the next position.
     * If the cursor references an item no longer in the list (e.g., due to deletion), resets to the first page.
     * </p>
     * <p>
     * Callers must pass an already-sorted list; use {@link #paginate(Iterable, String, int, Function)}
     * to sort and paginate in one step.
     * </p>
     *
     * @param <T> the type of items in the list
     * @param sorted the sorted list of all items
     * @param cursorValue the cursor from the previous page, or null for the first page
     * @param pageSize the maximum number of items per page; 0 returns all remaining items
     * @param nameExtractor function to extract the name/identifier from each item
     * @return a sublist containing the items for the requested page
     * @throws IllegalArgumentException if {@code pageSize} is negative
     */
    private static <T> List<T> applyPage(List<T> sorted, String cursorValue, int pageSize, Function<T, String> nameExtractor) {
        if (pageSize < 0) {
            throw ROOT_LOGGER.invalidPageSize(pageSize);
        }
        int start = 0;
        if (cursorValue != null) {
            try {
                String lastName = decode(cursorValue);
                boolean found = false;
                int lo = 0, hi = sorted.size() - 1;
                while (lo <= hi) {
                    int mid = (lo + hi) >>> 1;
                    int cmp = nameExtractor.apply(sorted.get(mid)).compareTo(lastName);
                    if (cmp < 0) {
                        lo = mid + 1;
                    } else if (cmp > 0) {
                        hi = mid - 1;
                    } else {
                        start = mid + 1;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    ROOT_LOGGER.debugf("Pagination cursor references item no longer in list, resetting to first page: %s", lastName);
                }
            } catch (IllegalArgumentException e) {
                ROOT_LOGGER.debugf("Malformed pagination cursor (invalid base64), resetting to first page: %s", cursorValue);
                start = 0;
            }
        }
        if (pageSize > 0 && start + pageSize < sorted.size()) {
            return sorted.subList(start, start + pageSize);
        }
        return sorted.subList(start, sorted.size());
    }

    /**
     * Generates the cursor for the next page, or returns null if the current page is the last one.
     * <p>
     * Compares the last item in the current page with the last item in the complete list.
     * If they differ, there are more items to fetch, so a cursor is generated from the last
     * item in the current page. If they are the same, the current page is the final page.
     * </p>
     *
     * @param <T> the type of items in the lists
     * @param all the complete sorted list of all items
     * @param page the current page of items
     * @param pageSize the maximum number of items per page
     * @param nameExtractor function to extract the name/identifier from each item
     * @return an encoded cursor for the next page, or null if this is the last page
     * @throws IllegalArgumentException if {@code pageSize} is negative
     */
    private static <T> String nextCursor(List<T> all, List<T> page, int pageSize, Function<T, String> nameExtractor) {
        if (pageSize < 0) {
            throw ROOT_LOGGER.invalidPageSize(pageSize);
        }
        if (pageSize > 0 && !page.isEmpty() && !all.isEmpty()) {
            T last = page.get(page.size() - 1);
            if (!nameExtractor.apply(last).equals(nameExtractor.apply(all.get(all.size() - 1)))) {
                return encode(nameExtractor.apply(last));
            }
        }
        return null;
    }

    /**
     * Result of a paginated query containing the items in the current page and an optional cursor for the next page.
     * <p>
     * If {@code nextCursor} is null, the current page is the last page. Otherwise, clients should
     * include the cursor value in their next request to fetch the subsequent page.
     * </p>
     *
     * @param <T> the type of items in the page
     * @param items the list of items in the current page
     * @param nextCursor the cursor to use for fetching the next page, or null if this is the last page
     */
    public record Page<T>(List<T> items, String nextCursor) {
    }

    /**
     * Performs cursor-based pagination on an iterable source of items.
     * <p>
     * This is a convenience method that:
     * <ol>
     *   <li>Sorts the source items by name (using the nameExtractor)</li>
     *   <li>Applies cursor-based pagination to extract the requested page</li>
     *   <li>Generates the cursor for the next page (if applicable)</li>
     * </ol>
     * </p>
     *
     * @param <T> the type of items to paginate
     * @param source the iterable source of all items
     * @param cursorValue the cursor from the previous page, or null for the first page
     * @param pageSize the maximum number of items per page
     * @param nameExtractor function to extract the name/identifier from each item for sorting and cursor generation
     * @return a {@link Page} containing the items for the requested page and an optional next cursor
     */
    public static <T> Page<T> paginate(Iterable<T> source, String cursorValue, int pageSize, Function<T, String> nameExtractor) {
        List<T> sorted = StreamSupport.stream(source.spliterator(), false)
                .sorted(Comparator.comparing(nameExtractor))
                .toList();
        List<T> page = applyPage(sorted, cursorValue, pageSize, nameExtractor);
        String next = nextCursor(sorted, page, pageSize, nameExtractor);
        return new Page<>(page, next);
    }
}
