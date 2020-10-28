package de.hso.sem.rw;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.tika.exception.TikaException;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Path("/document")
public class DocumentResource {

    @Inject
    private Repository repository;

    @Inject
    private FullTextIndex fullTextIndex;

    @GET
    @Path("/{id}/")
    public Response get(@PathParam("id") Integer id) throws IOException {
        Repository.GetResult result = repository.get(id);
        return Response.ok(result.getFile(), result.getDocument().getMime())
                .build();
    }

    @POST
    @Path("/")
    public Response download(InputStream body) throws IOException, TikaException, SAXException {
        repository.put(body);
        return Response.ok("Ok").build();
    }

    @GET
    @Path("/find")
    @Produces(MediaType.APPLICATION_JSON)
    public List<FullTextIndex.QueryResult> find(@QueryParam("q") String query) throws ParseException, InvalidTokenOffsetsException, IOException {
        return fullTextIndex.query(query);
    }
}
