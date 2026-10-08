package com.igot.cb.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class BASE64DecoderTest {

    @Test
    void testBytesPerAtom() {
        BASE64Decoder decoder = new BASE64Decoder();
        assertEquals(4, decoder.bytesPerAtom());
    }

    @Test
    void testBytesPerLine() {
        BASE64Decoder decoder = new BASE64Decoder();
        assertEquals(72, decoder.bytesPerLine());
    }

    @ParameterizedTest
    @CsvSource({
            "QWxs, All",
            "QWw=, Al",
            "QQ==, A"
    })
    void testDecodeAtomWithVariousPadding(String input, String expected) throws IOException {
        BASE64Decoder decoder = new BASE64Decoder();
        PushbackInputStream inputStream = new PushbackInputStream(new ByteArrayInputStream(input.getBytes()));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        decoder.decodeAtom(inputStream, outputStream, 4);

        byte[] result = outputStream.toByteArray();
        assertEquals(expected, new String(result));
    }

    @Test
    void testDecodeAtomWithInsufficientBytes() {
        BASE64Decoder decoder = new BASE64Decoder();
        String input = "Q";
        PushbackInputStream inputStream = new PushbackInputStream(new ByteArrayInputStream(input.getBytes()));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        assertThrows(IOException.class, () -> {
            decoder.decodeAtom(inputStream, outputStream, 1);
        });
    }

    @Test
    void testDecodeAtomWithNewlines() throws IOException {
        BASE64Decoder decoder = new BASE64Decoder();
        String input = "\nQ\rW\nxs"; // base64 with newlines
        PushbackInputStream inputStream = new PushbackInputStream(new ByteArrayInputStream(input.getBytes()));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        decoder.decodeAtom(inputStream, outputStream, 4);
        
        byte[] result = outputStream.toByteArray();
        assertNotNull(result);
    }

    @Test
    void testDecodeBufferWithValidBase64() throws IOException {
        BASE64Decoder decoder = new BASE64Decoder();
        String input = "SGVsbG8gV29ybGQ="; // "Hello World" in base64
        
        byte[] result = decoder.decodeBuffer(input);
        assertEquals("Hello World", new String(result));
    }

    @Test
    void testDecodeBufferInputStream() throws IOException {
        BASE64Decoder decoder = new BASE64Decoder();
        String input = "SGVsbG8="; // "Hello" in base64
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        
        byte[] result = decoder.decodeBuffer(inputStream);
        assertEquals("Hello", new String(result));
    }

    @Test
    void testDecodeAtomWithEmptyStream() {
        BASE64Decoder decoder = new BASE64Decoder();
        PushbackInputStream inputStream = new PushbackInputStream(new ByteArrayInputStream(new byte[0]));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        assertThrows(IOException.class, () -> {
            decoder.decodeAtom(inputStream, outputStream, 4);
        });
    }
}