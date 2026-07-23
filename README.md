# Narro

Narro는 단말의 텍스트 파일을 Android 시스템 음성으로 읽어 주는 Kotlin·Jetpack Compose 앱입니다. 현재 저장소에는 설계서 v0.3을 기준으로 동작하는 Android MVP가 포함되어 있습니다.

## 구현 범위

- UTF-8, EUC-KR 계열, UTF-16LE/BE 텍스트 가져오기와 UTF-8 정규화
- 빈 파일, 바이너리, 지원하지 않는 인코딩, 20 MiB 초과 파일 검사
- 전체 파일을 메모리에 올리지 않는 스트리밍 변환과 문장·표시 구간 인덱싱
- Room 기반 문서, 구간, 진행 위치, 북마크 저장
- 이전·현재·다음 3개 구간만 로드하는 Compose 읽기 화면
- Foreground Service와 Android 시스템 TTS를 이용한 백그라운드 재생
- 오디오 포커스 상실, 출력 장치 분리, 알림 재생 제어 처리
- 정상 중지 시 마지막 완료 위치 저장 및 문서 재진입 시 위치 복원
- 4자리 PIN, PBKDF2-HMAC-SHA-256, Android Keystore 기반 앱 잠금
- 선택적 생체인증, 읽기 속도 설정
- Android Auto Backup 규칙과 보안 데이터 백업 제외
- 시스템 언어 및 Android 앱 언어 설정을 따르는 영어·한국어 UI
- Galaxy S24 Ultra를 포함한 창 너비 기반 적응형 화면

## 개발 환경

- Android Studio 2026.1 계열 권장
- JDK 17 이상
- Android SDK 36
- 최소 지원 버전 Android 8.0(API 26)

저장소에는 Gradle Wrapper가 포함되어 있으므로 별도 Gradle 설치는 필요하지 않습니다.

## 빌드와 검증

Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

macOS 또는 Linux:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

디버그 APK는 빌드 후 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## 프로젝트 구조

```text
app/src/main/java/com/narro/app/
├─ data/         # Room, DataStore, 파일 변환과 구간 파서
├─ domain/       # 앱 모델과 저장소 계약
├─ feature/      # Compose 화면과 화면 상태
├─ playback/     # TTS Foreground Service와 재생 상태
├─ security/     # PIN 파생키와 Keystore 저장
└─ core/ui/      # 공통 테마
```

상세 요구사항과 설계 결정은 [`narro_design_v3.0.md`](narro_design_v3.0.md), 사용자 메시지 기준은 [`message_ko.md`](message_ko.md)와 [`message_en.md`](message_en.md)를 참고합니다.

## 실기기 확인 항목

자동 빌드·단위 테스트·Lint와 Galaxy S24 Ultra 에뮬레이터 흐름 검증을 수행합니다. 출시 전에는 Galaxy S24 Ultra 실기기에서 다음 항목을 추가 확인해야 합니다.

- 화면 잠금 및 장시간 백그라운드 TTS 재생
- 유선 이어폰·Bluetooth 장치 분리와 오디오 포커스 전환
- 제조사별 TTS 음성 설치 및 언어 전환
- Google Drive 기기 백업과 복원
- 20 MiB 파일의 메모리·구간 전환 성능 수용 기준
