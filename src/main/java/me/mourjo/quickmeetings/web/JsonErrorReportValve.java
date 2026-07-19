package me.mourjo.quickmeetings.web;

import java.io.IOException;
import java.io.Writer;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.http.MediaType;

/**
 * Custom Tomcat ErrorReportValve that replaces the default HTML error page with a JSON
 * response. This handles errors rejected at the Tomcat connector level (e.g., encoded
 * slash characters, malformed URIs) that occur before any servlet filter or Spring MVC
 * processing.
 */
public class JsonErrorReportValve extends ErrorReportValve {

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        if (!response.setErrorReported()) {
            return;
        }

        try {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            Writer writer = response.getReporter();
            if (writer != null) {
                String message = response.getMessage();
                if (message == null) {
                    message = "Bad Request";
                }
                writer.write("{\"message\":\"%s\"}".formatted(
                    message.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                ));
                response.finishResponse();
            }
        } catch (IOException e) {
            // Cannot write response — nothing else to do
        }
    }
}
