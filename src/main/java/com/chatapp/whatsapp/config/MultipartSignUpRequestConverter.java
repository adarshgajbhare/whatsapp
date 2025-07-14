package com.chatapp.whatsapp.config;

import com.chatapp.whatsapp.dto.SignUpRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class MultipartSignUpRequestConverter implements HttpMessageConverter<SignUpRequest> {

    private final ObjectMapper objectMapper;

    public MultipartSignUpRequestConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return SignUpRequest.class.isAssignableFrom(clazz) && 
               (MediaType.MULTIPART_FORM_DATA.includes(mediaType) || 
                MediaType.APPLICATION_OCTET_STREAM.includes(mediaType));
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return false; // We don't need to write SignUpRequest objects
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        List<MediaType> supportedMediaTypes = new ArrayList<>();
        supportedMediaTypes.add(MediaType.MULTIPART_FORM_DATA);
        supportedMediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
        return supportedMediaTypes;
    }

    @Override
    public SignUpRequest read(Class<? extends SignUpRequest> clazz, HttpInputMessage inputMessage) 
            throws IOException, HttpMessageNotReadableException {
        try {
            // Try standard JSON deserialization first
            return objectMapper.readValue(inputMessage.getBody(), SignUpRequest.class);
        } catch (Exception e) {
            // If that fails, create an empty SignUpRequest
            // The actual data will be bound by Spring from the @RequestPart annotation
            return new SignUpRequest();
        }
    }

    @Override
    public void write(SignUpRequest signUpRequest, MediaType contentType, HttpOutputMessage outputMessage) 
            throws IOException, HttpMessageNotWritableException {
        // Not implemented as we don't need to write SignUpRequest objects
    }
}
