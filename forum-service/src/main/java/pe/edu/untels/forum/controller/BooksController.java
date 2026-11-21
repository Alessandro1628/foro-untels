package pe.edu.untels.forum.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import pe.edu.untels.forum.client.BooksClient;

@RestController
@RequestMapping("/api/v1/books")
public class BooksController {
    private final BooksClient books;
    public BooksController(BooksClient books) { this.books = books; }
    @GetMapping
    public List<BooksClient.Book> search(@RequestParam String q) { return books.search(q); }
}
