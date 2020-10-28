package de.hso.sem.rw;

import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.xml.sax.SAXException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class Repository {

    @Inject
    private FullTextIndex index;

    private Path uploadDirectory = Path.of("upload");

    public Repository() {
        try {
            Files.createDirectory(uploadDirectory);
        } catch (IOException e) {
            // ignore. Directory already present.
        }
    }

    public void put(InputStream is) throws IOException, TikaException, SAXException {
        Long id = System.nanoTime();
        Path target = uploadDirectory.resolve(id.toString()+".bin");
        FileUtils.copyInputStreamToFile(is, target.toFile());
        index.add(target, id);
    }

    public GetResult get(int id) throws IOException {
        FullTextIndex.IndexDocument document = index.get(id);
        Path target = uploadDirectory.resolve(document.getBlobId()+".bin");

        return new GetResult(target.toFile(), document);
    }

    public static class GetResult {
        private final File file;

        private final FullTextIndex.IndexDocument document;

        public GetResult(File file, FullTextIndex.IndexDocument document) {
            this.file = file;
            this.document = document;
        }

        public File getFile() {
            return file;
        }

        public FullTextIndex.IndexDocument getDocument() {
            return document;
        }
    }
}
