package pe.edu.untels.forum.client;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BooksClient {
    public record Book(String id, String title, List<String> authors, String previewUrl) {}
    record Access(String viewability) {}
    record Info(String title, List<String> authors) {}
    record Volume(String id, Info volumeInfo, Access accessInfo) {
        Book book() {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || volumeInfo == null ||
                    volumeInfo.title() == null || volumeInfo.title().isBlank()) return null;
            return new Book(id, volumeInfo.title(), volumeInfo.authors() == null ? List.of() : volumeInfo.authors(),
                accessInfo!=null && ("PARTIAL".equals(accessInfo.viewability()) || "ALL_PAGES".equals(accessInfo.viewability()))
                ? "https://books.google.com/books?id="+id+"&printsec=frontcover" : null);
        }
    }
    record Results(List<Volume> items) {}

    private final RestClient client;
    private final String key;

    public BooksClient(@Value("${books.base-url:https://www.googleapis.com/books/v1}") String base,
                       @Value("${GOOGLE_BOOKS_API_KEY:}") String key) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        client = RestClient.builder().baseUrl(base).requestFactory(factory).build();
        this.key = key;
    }

    public List<Book> search(String term) {
        if (term == null || term.isBlank() || term.length() > 150)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe hasta 150 caracteres");
        try {
            Results results = client.get().uri(b -> {
                b.path("/volumes").queryParam("q", "{term}").queryParam("maxResults", 8);
                if (!key.isBlank()) b.queryParam("key", key);
                return b.build(term.strip());
            }).retrieve().body(Results.class);
            if (results == null) throw unavailable();
            return results.items() == null ? List.of() : results.items().stream()
                    .map(Volume::book).filter(java.util.Objects::nonNull).toList();
        } catch (RestClientException e) { throw unavailable(); }
    }

    public Book resolve(String id) {
        try {
            Volume volume = client.get().uri(b -> {
                b.path("/volumes/{id}");
                if (!key.isBlank()) b.queryParam("key", key);
                return b.build(id);
            }).retrieve().body(Volume.class);
            Book book = volume == null ? null : volume.book();
            if (book == null || !id.equals(book.id())) throw unavailable();
            return book;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referencia bibliográfica desconocida");
        } catch (RestClientException e) { throw unavailable(); }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google Books no está disponible");
    }
}
