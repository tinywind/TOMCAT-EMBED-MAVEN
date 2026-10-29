package org.tinywind.tomcat.util;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class EmbeddedTomcatTest {
    @Test(timeout = 30000)
    public void pluginStartsTheUpdatedRuntimeAndServesAServlet() throws Exception {
        Path root = Files.createTempDirectory(Paths.get("target"), "servlet-smoke-").toAbsolutePath();
        Path web = Files.createDirectories(root.resolve("web/WEB-INF"));
        String descriptor = "<web-app xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\" version=\"4.0\">"
                + "<servlet><servlet-name>smoke</servlet-name><servlet-class>"
                + SmokeServlet.class.getName() + "</servlet-class></servlet>"
                + "<servlet-mapping><servlet-name>smoke</servlet-name><url-pattern>/*</url-pattern>"
                + "</servlet-mapping></web-app>";
        Files.write(web.resolve("web.xml"), descriptor.getBytes(StandardCharsets.UTF_8));
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        File log = root.resolve("server.log").toFile();
        Process process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                Server.class.getName(), root.toString(), Integer.toString(port))
                .redirectErrorStream(true).redirectOutput(log).start();
        try {
            String body = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (body == null && process.isAlive() && System.nanoTime() < deadline) {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/").openConnection();
                    connection.setConnectTimeout(200);
                    connection.setReadTimeout(1000);
                    try (Scanner response = new Scanner(connection.getInputStream(), "UTF-8")) {
                        body = response.useDelimiter("\\A").next();
                    } finally { connection.disconnect(); }
                } catch (IOException exception) { Thread.sleep(100); }
            }
            assertEquals(new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8), "embedded-tomcat", body);
        } finally {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly().waitFor();
        }
    }

    public static class SmokeServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            response.getWriter().write("embedded-tomcat");
        }
    }

    public static class Server {
        public static void main(String[] args) throws Exception {
            RunWithoutCompileMojo mojo = new RunWithoutCompileMojo();
            mojo.project = new MavenProject();
            Build build = new Build();
            build.setOutputDirectory(new File("target/test-classes").getAbsolutePath());
            mojo.project.getModel().setBuild(build);
            mojo.port = Integer.valueOf(args[1]);
            mojo.contextPath = "";
            mojo.baseDir = new File(args[0], "web").getAbsolutePath();
            mojo.configurationDir = new File(args[0], "tomcat");
            mojo.dependencies = Collections.emptySet();
            mojo.pluginArtifacts = Collections.emptyList();
            mojo.execute();
        }
    }
}
