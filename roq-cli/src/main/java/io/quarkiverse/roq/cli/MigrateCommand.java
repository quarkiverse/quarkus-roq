package io.quarkiverse.roq.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.quarkiverse.roq.migration.engine.MigrationException;
import io.quarkiverse.roq.migration.engine.MigrationOptions;
import io.quarkiverse.roq.migration.engine.MigrationOutput;
import io.quarkiverse.roq.migration.engine.MigrationRunner;
import io.quarkiverse.roq.migration.engine.Scaffolder;
import io.quarkiverse.roq.migration.engine.SiteBuilder;
import io.quarkiverse.roq.migration.engine.SiteMigrator;
import io.quarkiverse.roq.migration.engine.SiteMigrators;
import io.quarkiverse.roq.migration.engine.TargetSite;
import io.quarkus.cli.common.OutputOptionMixin;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "migrate", mixinStandardHelpOptions = true, description = "Migrate a site from another static site "
        + "generator to Roq, detecting which one built it")
public class MigrateCommand implements Callable<Integer> {

    @CommandLine.Mixin(name = "output")
    OutputOptionMixin output;

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", arity = "0..1", description = "The site to migrate (default: the current directory)")
    Path source = Path.of(".");

    @ArgGroup(exclusive = true)
    Destination destination;

    static class Destination {
        @Option(names = { "-o", "--output-directory" }, description = "Where to write the migrated site (default: <site>-roq "
                + "next to the site). The output directory must be new or empty.")
        Path outputDir;

        @Option(names = "--in-place", description = "Migrate the site in its own directory. This is destructive, so make sure to commit first.")
        boolean inPlace;
    }

    @Option(names = "--no-build", description = "Don't build the migrated site")
    boolean noBuild;

    private final SiteMigrators migrators;
    private final Scaffolder scaffolder;
    private final SiteBuilder builder;

    public MigrateCommand() {
        this(SiteMigrators.builtIn(), new MigrationScaffolder(), null);
    }

    /**
     * @param builder builds the migrated site; null to build it as {@code roq generate} does
     */
    MigrateCommand(SiteMigrators migrators, Scaffolder scaffolder, SiteBuilder builder) {
        this.migrators = migrators;
        this.scaffolder = scaffolder;
        this.builder = builder;
    }

    @Override
    public Integer call() {
        Path sourceDir = source.toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceDir)) {
            output.error("No such directory: " + sourceDir);
            return CommandLine.ExitCode.USAGE;
        }
        Path outputDir = outputDir(sourceDir);
        try {
            SiteMigrator migrator = migrators.choose(sourceDir);
            output.info("🗿 Migrating the " + migrator.type().displayName() + " site in " + sourceDir
                    + (outputDir.equals(sourceDir) ? ", in place" : " to " + outputDir) + "...");
            MigrationOutput migrationOutput = new MigrationOutput() {
                @Override
                public void info(String message) {
                    output.info(message);
                }

                @Override
                public void warn(String message) {
                    output.warn(message);
                }
            };
            new MigrationRunner(migrator, sourceDir, scaffolder,
                    builder != null ? builder : this::generate,
                    new MigrationOptions(outputDir, !noBuild, migrationOutput)).run();
            output.info("");
            output.info("✨ Roq site migrated to " + outputDir);
            output.info("");
            return CommandLine.ExitCode.OK;
        } catch (MigrationException e) {
            output.error(e.getMessage());
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    /**
     * Builds the migrated site with {@code roq generate}, in the migrated site's directory.
     */
    private void generate(TargetSite site) throws Exception {
        CommandLine generate = spec.root().subcommands().get("generate");
        generate.parseArgs();
        if (((GenerateCommand) generate.getCommand()).generate(site.dir()) != 0) {
            throw new MigrationException("The migrated site in " + site.dir() + " did not build; fix it, then run "
                    + "'roq generate' there");
        }
    }

    private Path outputDir(Path sourceDir) {
        if (destination != null && destination.inPlace) {
            return sourceDir;
        }
        if (destination != null && destination.outputDir != null) {
            return destination.outputDir.toAbsolutePath().normalize();
        }
        return sourceDir.resolveSibling(sourceDir.getFileName() + "-roq");
    }
}
