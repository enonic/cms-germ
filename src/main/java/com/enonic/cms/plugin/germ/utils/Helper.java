package com.enonic.cms.plugin.germ.utils;

import com.enonic.cms.plugin.germ.GermPluginController;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Date;
import java.util.Random;

/**
 * User: rfo
 * Date: 10/10/13
 * Time: 3:32 PM
 */
public class Helper {

    public static String encryptPassword(String password){
        BASE64Encoder encoder = new BASE64Encoder();
        Random random = new Random((new Date()).getTime());
        // let's create some dummy salt
        byte[] salt = new byte[8];
        random.nextBytes(salt);
        return encoder.encode(salt)+
                encoder.encode(password.getBytes());
    }

    public static String decryptPassword(String encryptKey){
    // let's ignore the salt
        if (encryptKey.length() > 12) {
            String cipher = encryptKey.substring(12);
            BASE64Decoder decoder = new BASE64Decoder();
            try {
                return new String(decoder.decodeBuffer(cipher));
            } catch (IOException e) {
                //  throw new InvalidImplementationException(
                //    "Failed to perform decryption for key ["+encryptKey+"]",e);
            }
        }
        return null;
    }

    public static void serveCss(String css, HttpServletResponse response) throws Exception {
        InputStream in = GermPluginController.class.getResourceAsStream(css);
        response.setContentType("text/css");
        Helper.stream(in, response.getOutputStream());
    }


    public static long stream(InputStream input, OutputStream output) throws IOException {
        ReadableByteChannel inputChannel = null;
        WritableByteChannel outputChannel = null;

        try {
            inputChannel = Channels.newChannel(input);
            outputChannel = Channels.newChannel(output);
            ByteBuffer buffer = ByteBuffer.allocate(10240);
            long size = 0;

            while (inputChannel.read(buffer) != -1) {
                buffer.flip();
                size += outputChannel.write(buffer);
                buffer.clear();
            }

            return size;
        } finally {
            if (outputChannel != null) try {
                outputChannel.close();
            } catch (IOException ignore) { /**/ }
            if (inputChannel != null) try {
                inputChannel.close();
            } catch (IOException ignore) { /**/ }
        }
    }

    /**
     * Pretty print document.
     */
    public static void prettyPrint(Document doc)
            throws IOException {
        prettyPrint(doc, System.out);
    }

    /**
     * Pretty print document.
     */
    public static void prettyPrint(Document doc, OutputStream out)
            throws IOException {
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        outputter.output(doc, out);
    }

    public static void prettyPrint(Element el) throws IOException {
        prettyPrint(el, System.out);
    }

    public static void prettyPrint(Element el, OutputStream out) throws IOException {
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        outputter.output(el, out);
    }

    /**
     * Copy input to output. Return number of bytes copied.
     */
    public static int copy(InputStream in, OutputStream out)
            throws IOException {
        int copied = 0;
        byte[] buffer = new byte[1024];

        while (true) {
            int num = in.read(buffer);
            if (num > 0) {
                out.write(buffer, 0, num);
                copied += num;
            } else {
                break;
            }
        }

        return copied;
    }

    /**
     * Parse xml document.
     */
    public static Document parseXml(InputStream in)
            throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder();
        return builder.build(in);
    }

    /**
     * Copy to bytes.
     */
    public static byte[] copyToBytes(InputStream in)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out);
        return out.toByteArray();
    }

    /**
     * Load text file.
     */
    public static String copyToString(InputStream in)
            throws IOException {
        byte[] bytes = copyToBytes(in);
        return new String(bytes, "UTF-8");
    }

}
