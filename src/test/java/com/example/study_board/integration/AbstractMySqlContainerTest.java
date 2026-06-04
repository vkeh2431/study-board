package com.example.study_board.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer; // ⚠️ Testcontainers 2.x: org.testcontainers.containers.MySQLContainer는 deprecated, 모듈 패키지로 이동
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트를 실제 MySQL 8.4(docker-compose/prod와 동일 버전)에서 실행하기 위한 베이스 클래스.
 *
 * <p>H2는 방언이 달라 {@code V1~V3} 마이그레이션(ENGINE=InnoDB, DATETIME(6), AUTO_INCREMENT 등)을 돌릴 수 없어
 * test 프로필에선 Flyway를 끄고 create-drop을 써 왔다(Phase 13/14의 "커버리지 갭"). 이 베이스를 상속하면
 * {@code testcontainers} 프로필이 Flyway를 켜고 {@code ddl-auto=validate}로 전환하므로, 실 MySQL에 마이그레이션을
 * 적용한 뒤 엔티티 ↔ 스키마 일치를 자동 검증한다.
 *
 * <p><b>싱글톤 컨테이너 패턴</b>: {@code static} 컨테이너를 클래스 로드 시 1회만 기동하고
 * {@link DynamicPropertySource}로 접속 정보를 주입한다. 여러 서브클래스가 하나의 컨테이너를 공유해
 * MySQL 기동 비용(수십 초)을 1회로 줄인다. 컨테이너는 Testcontainers의 Ryuk가 JVM 종료 시 정리한다.
 * (단일 클래스라면 {@code @Testcontainers + @Container + @ServiceConnection}이 더 간결한 대안이다.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "testcontainers"}) // test=JWT시크릿/statistics/batch 유지, testcontainers=Flyway+validate로 덮어씀(뒤 프로필 우선)
public abstract class AbstractMySqlContainerTest {

    // Testcontainers 2.x의 새 모듈 클래스는 self-type 제네릭(SELF)을 제거 → 비제네릭으로 선언한다.
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    static {
        MYSQL.start();
    }

    /** 컨테이너 JDBC 접속 정보를 datasource 프로퍼티로 주입한다(application-test.properties의 H2 URL을 덮어씀). */
    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }
}
