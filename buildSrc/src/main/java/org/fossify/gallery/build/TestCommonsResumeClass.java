package org.fossify.gallery.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/** Puts the same patched Commons resume class on the host JVM's test classpath. */
public abstract class TestCommonsResumeClass extends DefaultTask {
    private static final String CLASS = "org.fossify.commons.activities.BaseSimpleActivity";
    private static final String ENTRY = CLASS.replace('.', '/') + ".class";

    @Classpath public abstract ConfigurableFileCollection getCommonsClasses();
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();

    @TaskAction public void patch() throws IOException {
        try (var jar = new ZipFile(getCommonsClasses().getSingleFile())) {
            var entry = jar.getEntry(ENTRY);
            if (entry == null) throw new IOException("Missing Commons class: " + ENTRY);
            try (var input = jar.getInputStream(entry)) {
                var reader = new ClassReader(input);
                // The replacement consumes and returns the same stack values as the original call.
                var writer = new ClassWriter(reader, 0);
                reader.accept(ClassicCommonsVisitorFactory.visitor(CLASS, writer), 0);
                Path output = getOutputDirectory().file(ENTRY).get().getAsFile().toPath();
                Files.createDirectories(output.getParent());
                Files.write(output, writer.toByteArray());
            }
        }
    }
}
