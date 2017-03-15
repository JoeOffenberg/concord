package com.walmartlabs.concord.plugins.fs;

import com.walmartlabs.concord.common.format.MultipleDefinitionParser;
import com.walmartlabs.concord.common.format.ParserException;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.model.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Filesystem based definition provider uses relative baseDir as a definition ID. It will scan, parse and cache
 * all process definition files in the specified directory.
 * <p>
 * <i>Limitations:</i> entry point names must be unique.
 */
public class FSDefinitionProvider implements ProcessDefinitionProvider {

    private static final Logger log = LoggerFactory.getLogger(FSDefinitionProvider.class);

    private final Map<String, ProcessDefinition> entryPoints;

    public FSDefinitionProvider(MultipleDefinitionParser parser, Map<String, String> attributes,
                                Path baseDir, String... fileTypes) throws ExecutionException {

        Map<String, ProcessDefinition> m = new HashMap<>();
        try {
            Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // skip "hidden" directories
                    String n = dir.getFileName().toString();
                    if (n.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // skip "hidden" files
                    String n = file.getFileName().toString();
                    if (n.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    if (matches(n, fileTypes)) {
                        parseAndStore(parser, attributes, m, file);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new ExecutionException("Error scanning the path '" + baseDir + "'", e);
        }

        this.entryPoints = m;
    }

    @Override
    public ProcessDefinition getById(String entryPoint) throws ExecutionException {
        ProcessDefinition pd = entryPoints.get(entryPoint);
        if (pd == null) {
            throw new ExecutionException("Can't find the entry point '%s'", entryPoint);
        }
        return pd;
    }

    private static boolean matches(String n, String[] fileTypes) {
        for (String t : fileTypes) {
            if (n.matches(t)) {
                return true;
            }
        }

        return false;
    }

    private static void parseAndStore(MultipleDefinitionParser parser, Map<String, String> attrs,
                                      Map<String, ProcessDefinition> m, Path f) throws IOException {

        try (InputStream in = Files.newInputStream(f)) {
            Collection<ProcessDefinition> pds = parser.parse(in);
            for (ProcessDefinition pd : pds) {
                m.put(pd.getId(), mergeAttrs(pd, attrs));
                log.info("parseAndStore ['{}'] -> got '{}'", f, pd.getId());
            }
        } catch (ParserException e) {
            log.warn("parseAndStore ['{}'] -> skipping, {}", f, e.getMessage());
        }
    }

    private static ProcessDefinition mergeAttrs(ProcessDefinition source, Map<String, String> attrs) {
        Map<String, String> m = new HashMap<>(source.getAttributes());
        m.putAll(attrs);
        return new ProcessDefinition(source, m);
    }
}
