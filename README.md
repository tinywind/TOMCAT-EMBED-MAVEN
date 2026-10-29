#maven plugin for embedded-tomcat

The embedded runtime uses Tomcat 9.0.123 and the javax.servlet API. The plugin artifact version is independent of the Tomcat runtime version.

#Configure Maven
```
    <build>
        <plugins>
            <plugin>
                <groupId>org.tinywind</groupId>
                <artifactId>tomcat-embed-maven</artifactId>
                <version>8.5.2</version>
                <configuration>
                    <port>80</port>
                    <contextPath>/</contextPath>
                    <baseDir>${project.basedir}/src/main/webapp</baseDir>
                </configuration>
            </plugin>
        </plugins>
    </build>
```

#Run
* mvn tomcat-embed-maven:run
* mvn tomcat-embed-maven:run-without-compile

#LICENSE
**Licensed under the Apache License, Version 2.0**