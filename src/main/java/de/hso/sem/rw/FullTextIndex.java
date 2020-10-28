package de.hso.sem.rw;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.document.Document;

import org.apache.lucene.store.MMapDirectory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.SAXException;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class FullTextIndex {

    private Analyzer analyzer = new StandardAnalyzer();
    private Directory directory;

    private static final String DOCUMENT_BLOB_ID = "id";
    private static final String DOCUMENT_MIME = "mime";
    private static final String DOCUMENT_TITLE = "title";
    private static final String DOCUMENT_AUTHOR = "author";
    private static final String DOCUMENT_DATE = "date";
    private static final String DOCUMENT_LANGUAGE = "lang";

    private static final String DOCUMENT_CONTENT = "content";

    public FullTextIndex() {
        try {
            directory = new MMapDirectory(Path.of("luceneIndex"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private IndexWriterConfig createIndexWriterConfig() {
        return new IndexWriterConfig(analyzer);
    }

    public void add(Path path, Long id) throws IOException, TikaException, SAXException {
        System.out.println("adding "+path+" id="+id+" to repo.");
        AutoDetectParser parser = new AutoDetectParser();

        ToTextContentHandler toTextContentHandler = new ToTextContentHandler();
        LanguageHandler languageHandler  = new LanguageHandler();

        Metadata metadata  =  new Metadata();

        // Analyze using Tika
        InputStream is = TikaInputStream.get(path);
        parser.parse(is, new TeeContentHandler(toTextContentHandler, languageHandler), metadata, new ParseContext());
        is.close();

        LanguageResult languageResult = languageHandler.getLanguage();

        // Write to Lucene
        IndexWriter indexWriter = new IndexWriter(directory, createIndexWriterConfig());

        Document document = new Document();
        document.add(new StringField(DOCUMENT_BLOB_ID, id.toString(), Field.Store.YES));
        document.add(new TextField(DOCUMENT_CONTENT, toTextContentHandler.toString(), Field.Store.YES));
        if(languageResult.isReasonablyCertain()) {
            document.add(new StringField(DOCUMENT_LANGUAGE, languageResult.getLanguage(), Field.Store.YES));
        }


        Map<String, String> map = new HashMap<>();
        map.put(Metadata.CONTENT_TYPE, DOCUMENT_MIME);
        map.put(DublinCore.TITLE.getName(), DOCUMENT_TITLE);
        map.put(DublinCore.CREATOR.getName(), DOCUMENT_AUTHOR);
        map.put(DublinCore.DATE.getName(), DOCUMENT_DATE);

        for(Map.Entry<String, String> e : map.entrySet()) {
            String value = metadata.get(e.getKey());
            if(value != null) {
                document.add(new StringField(e.getValue(),value, Field.Store.YES));
            }
        }

        indexWriter.addDocument(document);
        indexWriter.close();
    }

    public IndexDocument get(int id) throws IOException {
        IndexWriter indexWriter = new IndexWriter(directory, createIndexWriterConfig());
        IndexReader reader = DirectoryReader.open(indexWriter);
        IndexSearcher searcher = new IndexSearcher(reader);

        Document doc = searcher.doc(id);
        IndexDocument indexDocument = new IndexDocument(id, doc);
        indexWriter.close();

        return indexDocument;
    }

    public List<QueryResult> query(String query) throws ParseException, IOException, InvalidTokenOffsetsException {
        Query q;
        if(query.trim().length() > 0) {
            q = new QueryParser(DOCUMENT_CONTENT, analyzer).parse(query);
        } else {
            q = new MatchAllDocsQuery();
        }
        return query(q);
    }

    public List<QueryResult> query(Query q) throws IOException, InvalidTokenOffsetsException {
        IndexWriter indexWriter = new IndexWriter(directory, createIndexWriterConfig());

        IndexReader reader = DirectoryReader.open(indexWriter);
        IndexSearcher searcher = new IndexSearcher(reader);

        TopDocs docs = searcher.search(q, 25);

        SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter();
        Highlighter highlighter = new Highlighter(htmlFormatter, new QueryScorer(q));

        ArrayList<QueryResult> results = new ArrayList<>();
        for(ScoreDoc doc : docs.scoreDocs) {
            Document d = searcher.doc(doc.doc);
            String content = d.get(DOCUMENT_CONTENT);
            TokenStream tokenStream = TokenSources.getAnyTokenStream(searcher.getIndexReader(), doc.doc, DOCUMENT_CONTENT, analyzer);
            TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, content, true, 3);

            String snippedHtml = Stream.of(frag)
                    .map(TextFragment::toString)
                    .collect(Collectors.joining(" ... "));

            results.add(new QueryResult(
                    new IndexDocument(doc.doc, d),
                    snippedHtml
            ));
        }

        indexWriter.close();
        return results;
    }

    public static class IndexDocument {
        private final int id;
        private final Long blobId;
        private final String mime;
        private final String title;
        private final String author;
        private final String date;
        private final String lang;

        public IndexDocument(int id, Document document) {
            this.id = id;
            this.blobId = Long.valueOf(document.get(DOCUMENT_BLOB_ID));
            this.mime = document.get(DOCUMENT_MIME);
            this.title = document.get(DOCUMENT_TITLE);
            this.author = document.get(DOCUMENT_AUTHOR);
            this.date = document.get(DOCUMENT_DATE);
            this.lang = document.get(DOCUMENT_LANGUAGE);
        }

        public int getId() {
            return id;
        }

        public Long getBlobId() {
            return blobId;
        }

        public String getMime() {
            return mime;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public String getDate() {
            return date;
        }

        public String getLang() {
            return lang;
        }


    }

    public static class QueryResult {

        private final IndexDocument document;
        private final String snippedHtml;

        public QueryResult(IndexDocument document, String snippedHtml) {
            this.document = document;
            this.snippedHtml = snippedHtml;
        }

        public IndexDocument getDocument() {
            return document;
        }

        public String getSnippedHtml() {
            return snippedHtml;
        }

    }
}
