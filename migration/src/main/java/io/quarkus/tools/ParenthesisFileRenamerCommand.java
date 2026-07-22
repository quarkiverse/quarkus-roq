package io.quarkus.tools;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rename-parens", mixinStandardHelpOptions = true, version = "1.0", description = "Renames files with parentheses and updates HTML references")
public class ParenthesisFileRenamerCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory to scan (e.g. public/)")
    private java.nio.file.Path directory;

    public static void main(String... args) {
        int exitCode = new CommandLine(new ParenthesisFileRenamerCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!java.nio.file.Files.isDirectory(directory)) {
            System.err.println("Error: '" + directory + "' is not a directory");
            return 1;
        }

        ParenthesisFileRenamer renamer = new ParenthesisFileRenamer();
        ParenthesisFileRenamer.Result result = renamer.rename(directory);

        if (result.filesRenamed() > 0) {
            System.out.println("Renamed " + result.filesRenamed() + " files with parentheses, updated "
                    + result.htmlFilesUpdated() + " HTML files");
        }

        return 0;
    }
}
