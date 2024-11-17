package ku_rum.backend.domain.reservation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.validation.Valid;
import ku_rum.backend.domain.reservation.dto.ReservationStatus;
import ku_rum.backend.domain.user.application.UserService;
import ku_rum.backend.domain.user.dto.request.WeinLoginRequest;
import ku_rum.backend.domain.user.dto.response.WeinLoginResponse;
import ku_rum.backend.global.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;

import org.jsoup.nodes.Document;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    public BaseResponse<String> crawlReservationPage(WeinLoginRequest weinLoginRequest) {
        WebDriver driver = setupWebDriver();

        try {
            // 로그인 페이지 접근
            driver.get("https://wein.konkuk.ac.kr/common/user/login.do");
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));

            // 로그인 수행
            if (!performLogin(driver, weinLoginRequest)) {
                return BaseResponse.of(HttpStatus.UNAUTHORIZED, "로그인 실패! 아이디 또는 비밀번호를 확인해주세요.", null);
            }

            // 예약 페이지 접근 및 크롤링
            return crawlReservationData(driver);

        } catch (Exception e) {
            log.error("크롤링 중 오류 발생: {}", e.getMessage(), e);
            return BaseResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "예약 데이터 크롤링 중 오류가 발생했습니다.", null);
        } finally {
            driver.quit();
        }
    }
    private boolean performLogin(WebDriver driver, WeinLoginRequest weinLoginRequest) {
        try {
            // 아이디와 비밀번호 입력
            WebElement userIdField = driver.findElement(By.name("userId"));
            WebElement passwordField = driver.findElement(By.name("pw"));
            WebElement loginButton = driver.findElement(By.id("loginBtn"));

            userIdField.sendKeys(weinLoginRequest.getUserId());
            passwordField.sendKeys(weinLoginRequest.getPassword());
            loginButton.click();

            // 로그인 성공 여부 확인
            String currentUrl = driver.getCurrentUrl();

            return currentUrl.contains("index.do");
        } catch (Exception e) {
            log.error("로그인 중 오류 발생: {}", e.getMessage(), e);
            return false;
        }
    }

    private BaseResponse<String> crawlReservationData(WebDriver driver) {
        try {
            String targetUrl = "https://wein.konkuk.ac.kr/ptfol/cmnt/cube/findCubeResveStep1.do";
            driver.get(targetUrl);

            // URL 접근 확인 로그
            String currentUrl = driver.getCurrentUrl();
            if (!currentUrl.equals(targetUrl)) {
                log.warn("URL 접근 실패: 예상 URL: {}, 현재 URL: {}", targetUrl, currentUrl);
                return BaseResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "예약 페이지 접근 중 오류가 발생했습니다.", null);
            }
            log.info("URL 접근 성공: 현재 URL: {}", currentUrl);

            // "전체" 체크박스 확인 및 선택
            try {
                WebElement allCheckbox = driver.findElement(By.id("buildAll"));
                if (!allCheckbox.isSelected()) {
                    allCheckbox.click();
                    log.info("전체 체크박스를 선택했습니다.");
                } else {
                    log.info("전체 체크박스가 이미 선택되어 있습니다.");
                }
            } catch (NoSuchElementException e) {
                log.warn("전체 체크박스를 찾을 수 없습니다. 페이지 구조를 확인하세요.");
                return BaseResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "전체 체크박스 요소를 찾을 수 없습니다.", null);
            }

            // "선택 건물 예약 현황 조회" 버튼 클릭
            try {
                WebElement searchButton = driver.findElement(By.id("searchBtn"));
                searchButton.click();
                log.info("선택 건물 예약 현황 조회 버튼을 클릭했습니다.");
            } catch (NoSuchElementException e) {
                log.warn("조회 버튼을 찾을 수 없습니다. 페이지 구조를 확인하세요.");
                return BaseResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "조회 버튼 요소를 찾을 수 없습니다.", null);
            }

            // 5초 대기 후 타임 테이블 로드 확인
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            boolean isTimeTableLoaded = wait.until(driver1 -> {
                List<WebElement> timeTables = driver1.findElements(By.cssSelector(".time-table"));
                return !timeTables.isEmpty();
            });

            if (isTimeTableLoaded) {
                log.info("타임 테이블이 성공적으로 로드되었습니다.");
                String pageSource = driver.getPageSource();

                // 예약 상태 파싱 (옵션)
                parseReservationStatus(pageSource);

                return BaseResponse.of(HttpStatus.OK, "타임 테이블이 성공적으로 로드되었습니다.", pageSource);
            } else {
                log.warn("타임 테이블 로드 실패");
                return BaseResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "타임 테이블 로드 중 오류가 발생했습니다.", null);
            }
        } catch (TimeoutException e) {
            log.error("타임 테이블 로드 시간 초과: {}", e.getMessage());
            return BaseResponse.of(HttpStatus.REQUEST_TIMEOUT, "타임 테이블 로드 중 시간 초과가 발생했습니다.", null);
        } catch (Exception e) {
            log.error("예약 데이터 크롤링 중 오류 발생: {}", e.getMessage(), e);
            return BaseResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "예약 데이터 크롤링 중 오류가 발생했습니다.", null);
        }
    }

    private MultiValueMap<String, String> createRequestBody(WeinLoginRequest weinLoginRequest) {
        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("userId", weinLoginRequest.getUserId());
        requestBody.add("pw", weinLoginRequest.getPassword());
        requestBody.add("rtnUrl", ""); // 리다이렉트 후 이동할 URL 지정, 필요시 수정
        return requestBody;
    }

    private WebDriver setupWebDriver() {
//        WebDriverManager.chromedriver().browserVersion("130.0.6723.117").setup();

        // ChromeOptions 설정
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized"); // 브라우저 창 최대화
        options.addArguments("--disable-notifications"); // 알림 비활성화
        options.addArguments("--headless"); // (필요 시) 헤드리스 모드

        // ChromeDriver 생성
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10)); // 암묵적 대기 설정
        return driver;
    }

    private boolean isReservationPageLoaded(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        // 특정 예약 화면 요소 확인
        WebElement reservationElement = driver.findElement(By.cssSelector(".time-table__title"));
        return reservationElement.getText().contains("K-Cube");
    }

    private void parseReservationStatus(String html) {
        try {
            Document document = Jsoup.parse(html);

            // 1. 건물 정보 추출
            Elements buildingElements = document.select("ul.lental-able li label");
            log.info("건물 정보 Elements: {}", buildingElements);
            List<String> buildingInfo = new ArrayList<>();
            for (Element building : buildingElements) {
                buildingInfo.add(building.text());
            }
            log.info("건물 정보: {}", buildingInfo);

            // 2. 타임 테이블 데이터 추출
            Elements timeTableElements = document.select("div.time-table");
            log.info("타임 테이블 Elements: {}", timeTableElements);
            List<Map<String, Object>> timeTableData = new ArrayList<>();

            for (Element table : timeTableElements) {
                Map<String, Object> tableData = new HashMap<>();

                // 타임 테이블 제목 (ex: 공학관 K-Cube)
                Element titleElement = table.selectFirst("p.time-table__title");
                log.info("타임 테이블 제목 Element: {}", titleElement);
                String title = titleElement != null ? titleElement.text() : "제목 없음";
                tableData.put("title", title);

                // 호실 정보 (ex: 시간, 1호실(6인실), ...)
                Elements roomElements = table.select("div.class ol li");
                log.info("호실 정보 Elements: {}", roomElements);
                List<String> rooms = new ArrayList<>();
                for (Element room : roomElements) {
                    rooms.add(room.text());
                }
                tableData.put("rooms", rooms);

                // 시간별 예약 상태
                Elements hourElements = table.select("div.time ol.hour > li");
//                log.info("시간별 예약 상태 Elements: {}", hourElements);
                List<Map<String, Object>> timeSlots = new ArrayList<>();
                for (Element hour : hourElements) {
                    Map<String, Object> timeSlot = new HashMap<>();

                    // 시간 정보 (ex: 08:00)
                    Element timeElement = hour.selectFirst("strong");
                    log.info("시간 정보 Element: {}", timeElement);
                    String time = timeElement != null ? timeElement.text() : "시간 없음";
                    timeSlot.put("time", time);

                    // 각 호실 예약 상태
                    Elements roomStatusElements = hour.select("ul > li");
                    log.info("각 호실 예약 상태 Elements: {}", roomStatusElements);
                    List<String> roomStatuses = new ArrayList<>();
                    for (Element statusElement : roomStatusElements) {
                        if (statusElement.hasClass("nochoice")) {
                            roomStatuses.add("예약 불가");
                        } else if (statusElement.hasClass("enable")) {
                            roomStatuses.add("예약 가능");
                        } else {
                            roomStatuses.add("예약 상태 미확인");
                        }
                    }
                    timeSlot.put("statuses", roomStatuses);
                    timeSlots.add(timeSlot);
                }
                tableData.put("timeSlots", timeSlots);

                timeTableData.add(tableData);
            }

            // 로그로 출력
            for (Map<String, Object> table : timeTableData) {
                log.info("타임 테이블 제목: {}", table.get("title"));
                log.info("호실 정보: {}", table.get("rooms"));
                List<Map<String, Object>> timeSlots = (List<Map<String, Object>>) table.get("timeSlots");
                for (Map<String, Object> slot : timeSlots) {
                    log.info("시간: {}, 상태: {}", slot.get("time"), slot.get("statuses"));
                }
            }
        } catch (Exception e) {
            log.error("예약 상태 파싱 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}

//        for (org.jsoup.nodes.Element roomElement : rooms) {
//            String roomName = roomElement.text();
//            org.jsoup.select.Elements timeSlots = times.select("li");
//
//            for (org.jsoup.nodes.Element timeSlot : timeSlots) {
//                String time = timeSlot.select(".time").text();
//                String status = timeSlot.select(".status").text();
//
//                // 예약 상태를 출력
//                log.info("Room: {}, Time: {}, Status: {}", roomName, time, status);
//            }
//        }

//        BaseResponse<WeinLoginResponse> loginResponse = loginToWein(weinLoginRequest);
//
//        if (!loginResponse.getData().isSuccess()) {
//            return BaseResponse.of(HttpStatus.UNAUTHORIZED, "Login failed, cannot proceed to reservation page.");
//        }
//
//        WebDriver driver = setupWebDriver();
//        driver.get("https://wein.konkuk.ac.kr");
////        driver.get("https://wein.konkuk.ac.kr/common/user/login.do"); // 로그인 페이지 접근
//        // 쿠키 추가
//        for (Cookie cookie : cookieStore.getCookies()) {
//            log.info("Cookie being added to WebDriver - Name: {}, Value: {}, Domain: {}, Path: {}",
//                    cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
//            org.openqa.selenium.Cookie seleniumCookie = new org.openqa.selenium.Cookie(
//                    cookie.getName(), cookie.getValue(), cookie.getDomain(),
//                    cookie.getPath(), cookie.getExpiryDate(), cookie.isSecure());
//            driver.manage().addCookie(seleniumCookie);
//        }
//        driver.get("https://wein.konkuk.ac.kr/ptfol/cmnt/cube/findCubeResveStep1.do"); // 바로 예약 페이지로 이동
//
//
//        String pageSource = driver.getPageSource();
//        if (pageSource.contains("로그인") || pageSource.contains("login")) {
//            log.warn("User is still on login page, session might not have been transferred correctly.");
//        }
//
//        try {
//            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
//            wait.until(ExpectedConditions.attributeToBe(By.tagName("body"), "class", "vsc-initialized"));
//            WebElement allCheckbox = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("buildAll")));
//            if (!allCheckbox.isSelected()) {
//                allCheckbox.click();
//            }
//
//            // "선택 건물 예약 현황 조회" 버튼 클릭
//            WebElement searchButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("searchBtn")));
//            searchButton.click();
//
//            // 검색 결과가 로드될 때까지 기다림
//            new WebDriverWait(driver, Duration.ofSeconds(5))
//                    .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".time-table__title")));
//
//            // HTML 소스 가져오기 및 Jsoup 파싱
//            String html = driver.getPageSource();
//            parseReservationStatus(html);
//
//            return BaseResponse.ok("Crawling and outputting reservation status completed successfully.");
//        } catch (TimeoutException e) {
//            log.error("Timeout occurred while waiting for an element: {}", e.getMessage());
//            return BaseResponse.of(HttpStatus.REQUEST_TIMEOUT, "Timeout while waiting for page elements to load.");
//        } catch (Exception e) {
//            log.error("An unexpected error occurred: {}", e.getMessage());
//            return BaseResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing reservation data.");
//        } finally {
//            driver.quit();
//        }

//    }


//    private BaseResponse<WeinLoginResponse> loginToWein(@Valid final WeinLoginRequest weinLoginRequest) {
//        CloseableHttpClient httpClient = HttpClients.custom()
//                .setRedirectStrategy(new DefaultRedirectStrategy())
//                .setDefaultCookieStore(cookieStore) // 쿠키 저장소 설정
//                .build();
//
//        // RestTemplate 설정
//        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
//        RestTemplate restTemplate = new RestTemplate(requestFactory);
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//
//        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(createRequestBody(weinLoginRequest), headers);
//        log.info("Attempting login with userId: {} and password: {}", weinLoginRequest.getUserId(), weinLoginRequest.getPassword());
//
//        try {
//            ResponseEntity<String> response = restTemplate.exchange(
//                    WEIN_LOGIN_URL,
//                    HttpMethod.POST,
//                    requestEntity,
//                    String.class
//            );
//
//            String responseBody = response.getBody();
//            if (responseBody != null) {
//                if (responseBody.contains("index.do")) {
//                    log.info("Login successful for userId: {}", weinLoginRequest.getUserId());
//                    return BaseResponse.ok(new WeinLoginResponse(true, "Wein login successful"));
//                } else if (responseBody.contains("login.do")) {
//                    log.warn("Login failed for userId: {}", weinLoginRequest.getUserId());
//                    return BaseResponse.of(HttpStatus.UNAUTHORIZED, new WeinLoginResponse(false, "Invalid Wein credentials"));
//                }
//            }
//
//            log.error("Unexpected response for userId: {}, status code: {}, response body: {}", weinLoginRequest.getUserId(), response.getStatusCode(), response.getBody());
//            return BaseResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, new WeinLoginResponse(false, "Unexpected login response"));
//
//        } catch (Exception e) {
//            log.error("Exception occurred during login: ", e);
//            return BaseResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, new WeinLoginResponse(false, "Wein login failed due to server error"));

//        }
//    }

