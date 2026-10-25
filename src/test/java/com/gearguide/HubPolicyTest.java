package com.gearguide;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Regression checks for the Hub's forbidden APIs in production bytecode only. */
public class HubPolicyTest {
    private static final Set<String> FORBIDDEN_CLASSES =
            Set.of(
                    "java/io/File",
                    "java/io/FileInputStream",
                    "java/io/FileOutputStream",
                    "java/io/FileReader",
                    "java/io/FileWriter",
                    "java/io/RandomAccessFile",
                    "java/nio/file/Files",
                    "java/nio/file/Path",
                    "java/nio/file/Paths",
                    "javax/swing/JFileChooser",
                    "net/runelite/client/util/Filepath$Unchecked",
                    "java/net/ServerSocket",
                    "java/net/Socket",
                    "java/net/HttpURLConnection",
                    "java/net/URLConnection",
                    "java/lang/Runtime",
                    "java/lang/ProcessBuilder",
                    "java/awt/KeyboardFocusManager");

    private static final Set<String> FORBIDDEN_METHODS =
            Set.of(
                    "java/lang/Thread.sleep",
                    "java/lang/Thread.interrupt",
                    "java/awt/Desktop.open",
                    "java/awt/Desktop.browse",
                    "net/runelite/client/util/LinkBrowser.open",
                    "net/runelite/api/Client.menuAction",
                    "com/google/gson/Gson.<init>",
                    "okhttp3/OkHttpClient.<init>",
                    "okhttp3/OkHttpClient$Builder.<init>");

    @Test
    public void productionDoesNotUseForbiddenFileNetworkOrRuntimeApis() throws Exception {
        Path classes = Path.of("build/classes/java/main");
        assertTrue(
                "Compile production classes before checking Hub policy",
                Files.isDirectory(classes));
        List<Path> files = new ArrayList<>();
        try (var paths = Files.walk(classes)) {
            paths.filter(p -> p.toString().endsWith(".class")).forEach(files::add);
        }
        assertFalse("No production classes found", files.isEmpty());
        List<String> violations = new ArrayList<>();
        for (Path file : files) check(file, violations);
        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    private static void check(Path file, List<String> violations) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            assertEquals(0xCAFEBABE, input.readInt());
            input.readUnsignedShort();
            input.readUnsignedShort();
            int count = input.readUnsignedShort();
            int[] tags = new int[count];
            Object[] values = new Object[count];
            for (int i = 1; i < count; i++) {
                int tag = tags[i] = input.readUnsignedByte();
                switch (tag) {
                    case 1:
                        values[i] = input.readUTF();
                        break;
                    case 7:
                        values[i] = input.readUnsignedShort();
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                        values[i] =
                                new int[] {input.readUnsignedShort(), input.readUnsignedShort()};
                        break;
                    case 3:
                    case 4:
                    case 17:
                    case 18:
                        input.readInt();
                        break;
                    case 5:
                    case 6:
                        input.readLong();
                        i++;
                        break;
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        input.readUnsignedShort();
                        break;
                    case 15:
                        input.readUnsignedByte();
                        input.readUnsignedShort();
                        break;
                    default:
                        throw new IOException("Unexpected constant pool tag " + tag);
                }
            }
            for (int i = 1; i < count; i++) {
                if (tags[i] == 7) {
                    String name = (String) values[(Integer) values[i]];
                    if (FORBIDDEN_CLASSES.contains(name)
                            || name.startsWith("java/lang/reflect/")
                            || name.startsWith("java/net/http/")
                            || name.startsWith("com/sun/net/httpserver/")
                            || name.startsWith("com/sun/jna/")) {
                        violations.add(file.getFileName() + ": " + name);
                    }
                } else if (tags[i] == 10 || tags[i] == 11) {
                    int[] ref = (int[]) values[i];
                    String owner = (String) values[(Integer) values[ref[0]]];
                    int[] method = (int[]) values[ref[1]];
                    String name = owner + "." + values[method[0]];
                    if (FORBIDDEN_METHODS.contains(name))
                        violations.add(file.getFileName() + ": " + name);
                }
            }
        }
    }
}
