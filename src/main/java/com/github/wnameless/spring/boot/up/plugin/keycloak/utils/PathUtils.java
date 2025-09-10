package com.github.wnameless.spring.boot.up.plugin.keycloak.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for path manipulation operations.
 * 
 * <p>Provides helper methods for joining and normalizing URL/file paths,
 * handling path separators correctly to avoid duplicate or missing slashes.
 * 
 * @author Wei-Ming Wu
 * @since 1.0.0
 */
public final class PathUtils {

  /**
   * Private constructor to prevent instantiation of utility class.
   */
  private PathUtils() {}

  /**
   * Joins multiple path segments into a single normalized path.
   * 
   * <p>This method properly handles path separators, ensuring:
   * <ul>
   *   <li>No duplicate slashes between path segments</li>
   *   <li>Proper slash placement between segments</li>
   *   <li>Removal of trailing slashes from intermediate segments</li>
   *   <li>Removal of leading slashes from subsequent segments</li>
   * </ul>
   * 
   * <p>Example:
   * <pre>
   * PathUtils.joinPath("/base/", "/path/", "file.txt") returns "/base/path/file.txt"
   * </pre>
   * 
   * @param path the base path
   * @param paths additional path segments to join
   * @return the joined and normalized path string
   */
  public static String joinPath(String path, String... paths) {
    String pathSeprator = "/";

    List<String> list = new ArrayList<>(Arrays.asList(paths));
    list.add(0, path);
    for (int i = 1; i < list.size(); i++) {
      int predecessor = i - 1;
      while (list.get(predecessor).endsWith(pathSeprator)) {
        list.set(predecessor,
            list.get(predecessor).substring(0, list.get(predecessor).length() - 1));
      }
      while (list.get(i).startsWith(pathSeprator)) {
        list.set(i, list.get(i).substring(1));
      }
      list.set(i, pathSeprator + list.get(i));
    }

    StringBuilder sb = new StringBuilder();
    list.stream().forEach(p -> sb.append(p));
    return sb.toString();
  }

}
