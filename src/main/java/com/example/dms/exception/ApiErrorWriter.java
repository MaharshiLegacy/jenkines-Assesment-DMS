package com.example.dms.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Filters run before Spring MVC, so @RestControllerAdvice can't see their errors: write JSON by hand. */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper mapper;

    public void write(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), ApiError.of(status, message, req.getRequestURI(), null));
    }
}
