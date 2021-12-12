# WebFlux File Server [![CI](https://github.com/daggerok/webflux-file-server/actions/workflows/ci.yaml/badge.svg)](https://github.com/daggerok/webflux-file-server/actions/workflows/ci.yaml)
This repository demonstrates example of reactive WebFlux based file server

```bash
mvn spring-boot:start
http --ignore-stdin --form post :8080/upload file@README.md
http --ignore-stdin --download  get :8080/download/README.md --output target/README.md
cat target/README.md
mvn spring-boot:stop
```

## RTFM
* https://www.techgalery.com/2021/09/upload-and-download-rest-api-using.html
<!--
* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/2.6.1/maven-plugin/reference/html/)
* [Create an OCI image](https://docs.spring.io/spring-boot/docs/2.6.1/maven-plugin/reference/html/#build-image)
* [Coroutines section of the Spring Framework Documentation](https://docs.spring.io/spring/docs/5.3.13/spring-framework-reference/languages.html#coroutines)
-->
