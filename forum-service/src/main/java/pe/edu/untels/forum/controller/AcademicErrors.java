package pe.edu.untels.forum.controller;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class AcademicErrors {
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Void> duplicate() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
