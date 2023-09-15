package com.github.wnameless.spring.boot.up.plugin.keycloak.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PathUtils {

  private PathUtils() {}

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
