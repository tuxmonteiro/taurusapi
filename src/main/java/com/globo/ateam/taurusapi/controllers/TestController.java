package com.globo.ateam.taurusapi.controllers;

import com.globo.ateam.taurusapi.services.TaskRunnerService;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
@RestController@RequestMapping("/test")
public class TestController {

    private final TaskRunnerService taskRunnerService;
    private final Gson gson = new GsonBuilder().create();
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Path lastIdFile;

    public TestController(@Autowired TaskRunnerService taskRunnerService) {
        this.taskRunnerService = taskRunnerService;
        this.lastIdFile = Paths.get(taskRunnerService.getTaurusTmpDir() + "/last");
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> getTests(final HttpServletRequest request) throws IOException {
        log.info("GET /test");
        Path thanosTmpDir = Paths.get(taskRunnerService.getTaurusTmpDir());
        if (Files.exists(thanosTmpDir)) {
            final String requestUrl = request.getRequestURL().toString().replaceAll("/$", "");
            final Stream<String> listOfThanosTmpDir = Files.list(thanosTmpDir)
                    .filter(f -> Files.isDirectory(f))
                    .map(s -> requestUrl + "/" + s.toString().replaceAll(".*/", ""));
            return ResponseEntity.ok(gson.toJson(listOfThanosTmpDir.collect(Collectors.toList())));
        }
        return ResponseEntity.notFound().build();
    }

    @RequestMapping(value = "/{testId}", method = RequestMethod.GET)
    public ResponseEntity<?> getTest(@PathVariable("testId") Long testId) throws IOException {
        log.info("GET /test/" + testId);
        String confFile = taskRunnerService.getTaurusTmpDir() + "/" + testId;
        String resultFile = taskRunnerService.getTaurusTmpDir() + "/" + testId + "/result";
        if (Files.exists(Paths.get(resultFile))) {
            return ResponseEntity.ok(Files.readAllBytes(Paths.get(resultFile)));
        }
        if (Files.exists(Paths.get(confFile))) {
            return ResponseEntity.ok(ImmutableMap.of("status", "still running"));
        }
        return ResponseEntity.notFound().build();
    }

    @RequestMapping(method = RequestMethod.POST, consumes = { "application/json", "text/yaml", "application/yaml", "application/x-yaml" } )
    public synchronized ResponseEntity<?> createTest(final HttpServletRequest request) throws IOException {
        final byte[] body = ByteStreams.toByteArray(request.getInputStream());
        log.info("POST /test/");
        final String lastIdStr = (Files.exists(lastIdFile)) ? Files.lines(lastIdFile).findAny().orElse("0") : "-1";
        final long testId = Long.parseLong(lastIdStr) + 1L;
        try (BufferedWriter writer = Files.newBufferedWriter(lastIdFile)) {
            writer.write(String.valueOf(testId));
        }
        final URI locationURI = URI.create(request.getRequestURL().toString().replaceAll("/$", "") + "/" + testId);
        final String contentType = request.getContentType();
        taskRunnerService.put(testId, body, contentType);
        return ResponseEntity.created(locationURI).build();
    }

    @RequestMapping(method = RequestMethod.DELETE)
    public ResponseEntity<?> cleanup() throws IOException {
        log.info("DELETE /test");
        Path thanosTmpDir = Paths.get(taskRunnerService.getTaurusTmpDir());
        if (Files.exists(thanosTmpDir)) {
            Files.list(thanosTmpDir).filter(f -> Files.isDirectory(f)).forEach(path -> {
                try {
                    Files.walk(path).sorted(Comparator.reverseOrder()).forEach(f -> {
                        try {
                            log.warn("Removing " + f.toString());
                            Files.delete(f);
                        } catch (IOException e) {
                            log.error(e.getMessage(), e);
                        }
                    });
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            });
        }
        return ResponseEntity.noContent().build();
    }

}
