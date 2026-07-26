# 更新日誌

本專案所有值得注意的變更都會記錄在這個檔案。

格式基於 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)。

本專案**不**採用語意化版本，而是使用 `KEYCLOAK_MAJOR.KEYCLOAK_MINOR.SPRINGBOOT_MAJOR.RELEASE`
四段式版號 —— `26.7.3.0` 表示內嵌 Keycloak 26.7、對應 Spring Boot 3 這條線的第 0 次發行。
版號規則在 26.7.3.0 有所調整，詳見 [README](README.md#version-numbering)。

本專案與 [spring-boot-up-embedded-keycloak](https://github.com/wnameless/spring-boot-up-embedded-keycloak)
版本鎖步發行，兩者請使用相同版號；升級時請一併參閱該專案的更新日誌。

<!-- 發布流程：
     1. 將 [Unreleased] 改為 [x.y.z.w] - YYYY-MM-DD，並在其上方補一個新的空 [Unreleased]
     2. 更新檔案底部的比較連結
     3. 先發行 spring-boot-up-embedded-keycloak，再把本專案 pom.xml 中對它的依賴改成正式版
     4. mvn clean deploy   （會在上傳成功後自動建立並推送 vx.y.z.w tag）
-->

## [Unreleased]

升級自 24.3.0.0 時請注意：Keycloak 26.7 需要 Hibernate ORM 7、Jakarta Persistence 3.2、
Infinispan 16 與 Liquibase 4.33，而 Spring Boot 的 parent BOM 管理的是較舊的版本。由於 Maven 的
`dependencyManagement` 不具傳遞性，**每個使用本函式庫的專案都必須在自己的 `pom.xml` 重複這些 pin**，
完整清單見 [README](README.md)。

### 新增

- `PemUtils`：集中處理 X.509 憑證與 PKCS#8 私鑰的載入，取代原本散落在預設設定、產生用範本與
  測試設定三處的重複實作
- `SamlLoginFlowTest`：對內嵌 IdP 執行完整的 SP-initiated SAML 登入流程測試，涵蓋受保護頁面轉址、
  SAML authn request、Keycloak 登入表單提交、SAMLResponse 回傳 ACS，以及登入後的頁面存取

### 變更

- **版號規則調整**：由 `KEYCLOAK_MAJOR.SPRINGBOOT_MAJOR.MAJOR.MINOR`（例如 `24.3.0.0`）改為
  `KEYCLOAK_MAJOR.KEYCLOAK_MINOR.SPRINGBOOT_MAJOR.RELEASE`。舊規則無法表達 Keycloak 次版本的升級
  —— 同一個版號會涵蓋 Keycloak 26.3 到 26.7，但 26.6 移除了 Platform SPI、26.7 更換了所需的
  Hibernate 與 Infinispan 版本 —— 而且第二碼實際上被 Spring Boot 佔用，卻長得像函式庫版本。
  版號在規則變更前後仍然單調遞增
- 升級至 spring-boot-up-embedded-keycloak 26.7.3.0（內嵌 Keycloak 26.7.0）
- 升級至 Spring Boot 3.5.16 parent
- 移除 `spring-security-saml2-service-provider` 的版本 pin，改由 Boot 匯入的 spring-security-bom
  管理。原本 pin 的 6.5.3 在 Boot 3.5.5 下與其他 Spring Security 模組一致，但升級後其餘模組移動到
  6.5.11，pin 反而讓 saml2 落後八個修補版本，且混用了以整組發行與測試的模組
- 在 `dependencyManagement` 中 pin Hibernate ORM 7、Jakarta Persistence 3.2、Infinispan 16
  與 Liquibase 4.33
- `keycloak.plugin.serverCertPem`、`keycloak.plugin.appCertPem`、`keycloak.plugin.appPrivateKeyPem`
  現在接受 `classpath:` 與 `file:` 前綴。裸檔名維持從 classpath 解析，既有設定不受影響。
  使用 `file:` 可將私鑰放在封裝的應用程式之外 —— 打包進 artifact 的私鑰無法在不重新建置的情況下輪替，
  而且會隨 artifact 散布給每一個取得它的人
- ⚠️ **行為變更**：`KeycloakRealmBootstrap` 在 `keycloak-realm.json` 與三個 PEM 檔只有部分存在時，
  改為拋出例外並列出已存在與缺少的檔案。原本會逕行補產生缺少的部分，詳見下方「修復」
- `mvn deploy` 會在上傳成功後自動建立並推送 `v${project.version}` tag。發布 SNAPSHOT 時以
  `mvn deploy -DskipTag` 略過。預設為打 tag，是因為兩種失誤的代價不對稱：忘記加旗標會把版本推上
  Central（無法撤回）卻沒有 tag，而多打一個 SNAPSHOT tag 只需要刪掉

### 修復

- `KeycloakRealmBootstrap` 部分重新產生會造成憑證與私鑰不匹配。`keycloak-realm.json` 與三個 PEM 檔
  共用兩組 RSA 金鑰對 —— realm JSON 內嵌兩組，每個 PEM 各持有其中一半 —— 但原本的邏輯逐檔判斷是否需要
  產生，而 `SelfSignedX509Certificate` 每次建構都會產生全新的金鑰對。刪除 `keycloak-realm.json`
  重建 realm（更改 realm 名稱或 client id 的自然做法）會把新的金鑰對寫進 realm，磁碟上的 PEM 卻仍是舊的；
  單獨刪除 `app_certificate.pem` 則會讓憑證與 `app_private_key.pem` 毫無關聯。兩種情況在建置期都不會
  失敗，而是在登入時以 SAML 簽章被拒的形式浮現，且完全看不出與被刪除的檔案有關。四個檔案現在視為單一單位：
  全部存在則跳過、全部不存在則產生、只有部分存在則拒絕；所有內容在第一次寫檔前就完成產生，
  中途失敗不會留下半套檔案
- 憑證找不到時的錯誤訊息不再宣稱「Classpath: x NOT found」，而是列出所有可接受的位置寫法

## [24.3.0.0] - 2025-09-10

第一個採用 `KEYCLOAK.SPRINGBOOT.MAJOR.MINOR` 四段式版號的發行版本，搭配內嵌 Keycloak 24.x。
此版本之前的變更請參閱 git 歷史。

[Unreleased]: https://github.com/wnameless/spring-boot-up-keycloak-plugin/compare/v24.3.0.0...HEAD
[24.3.0.0]: https://github.com/wnameless/spring-boot-up-keycloak-plugin/releases/tag/v24.3.0.0
