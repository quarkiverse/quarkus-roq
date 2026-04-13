package io.quarkiverse.roq.theme.antoroq;

import java.time.LocalDate;
import java.util.List;

import io.quarkus.qute.TemplateExtension;

@TemplateExtension
public class AntoroqTemplateExtensions {

    public static String relativize(String to) {
        return relativize(to, null);
    }

    public static String relativize(String to, String from) {
        if (to == null || to.isEmpty())
            return "#";
        if (to.charAt(0) != '/')
            return to;
        if (from == null)
            return to;
        int hashIdx = to.indexOf('#');
        String hash = "";
        if (hashIdx >= 0) {
            hash = to.substring(hashIdx);
            to = to.substring(0, hashIdx);
        }
        if (to.equals(from))
            return hash.isEmpty() ? "./" : hash;

        int lastSlashFrom = from.lastIndexOf('/');
        String fromDir = lastSlashFrom > 0 ? from.substring(0, lastSlashFrom + 1) : "";
        String toDir = to.lastIndexOf('/') > 0 ? to.substring(0, to.lastIndexOf('/') + 1) : "";

        if (fromDir.equals(toDir)) {
            String toFile = to.substring(to.lastIndexOf('/') + 1);
            return hash.isEmpty() ? toFile : toFile + hash;
        }

        int depthFrom = (int) fromDir.chars().filter(c -> c == '/').count();
        int depthTo = (int) toDir.chars().filter(c -> c == '/').count();
        int diff = depthFrom - depthTo;

        StringBuilder rel = new StringBuilder();
        for (int i = 0; i < diff; i++) {
            rel.append("../");
        }
        if (!toDir.isEmpty()) {
            rel.append(to.substring(toDir.length()));
        }
        String result = rel.toString();
        if (result.isEmpty())
            result = "./";
        return result + hash;
    }

    public static int increment(Integer value) {
        return (value != null ? value : 0) + 1;
    }

    public static boolean and(Boolean... args) {
        if (args == null || args.length < 2)
            return false;
        for (Boolean arg : args) {
            if (!Boolean.TRUE.equals(arg))
                return false;
        }
        return true;
    }

    public static boolean or(Boolean... args) {
        if (args == null || args.length < 2)
            return false;
        for (Boolean arg : args) {
            if (Boolean.TRUE.equals(arg))
                return true;
        }
        return false;
    }

    public static boolean not(Boolean value) {
        return !Boolean.TRUE.equals(value);
    }

    public static boolean eq(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }

    public static boolean ne(Object a, Object b) {
        return !eq(a, b);
    }

    public static String detag(String html) {
        if (html == null)
            return "";
        return html.replaceAll("<[^>]*>", "");
    }

    public static String year() {
        return String.valueOf(LocalDate.now().getYear());
    }

    public static boolean contains(List<?> list, Object item) {
        if (list == null || item == null)
            return false;
        return list.contains(item);
    }

}
