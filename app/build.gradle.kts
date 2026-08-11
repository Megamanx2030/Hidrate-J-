// Import explicito: dentro do bloco android {} o nome "java" e capturado pela
// extensao do Gradle, e java.util.Properties deixa de resolver.
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "br.com.stefanosabino.hidrateja"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  /**
   * ASSINATURA DE PUBLICACAO.
   *
   * O bloco anterior lia a senha de variaveis de ambiente que nunca eram
   * definidas, entao storePassword ficava nulo e o release acabava assinado com
   * a CHAVE DE DEPURACAO (CN=Android Debug). A Play Store recusa esses arquivos
   * de cara.
   *
   * Agora os dados vem de keystore.properties, que fica na raiz do projeto e
   * esta no .gitignore. O proprio .jks mora FORA do repositorio, e o caminho
   * ate ele e absoluto: assim nem o arquivo nem as senhas tem como escapar num
   * "git add ." distraido.
   *
   * Sem o keystore.properties o projeto continua compilando (util para quem so
   * quer rodar o debug), mas o release sai SEM assinatura de publicacao, e o
   * aviso abaixo explica o porque.
   */
  val arquivoDeAssinatura = rootProject.file("keystore.properties")
  val dadosDeAssinatura = Properties().apply {
    if (arquivoDeAssinatura.exists()) {
      arquivoDeAssinatura.inputStream().use { load(it) }
    }
  }
  val temAssinaturaDePublicacao = dadosDeAssinatura.getProperty("storeFile") != null

  signingConfigs {
    if (temAssinaturaDePublicacao) {
      create("release") {
        storeFile = file(dadosDeAssinatura.getProperty("storeFile"))
        storePassword = dadosDeAssinatura.getProperty("storePassword")
        keyAlias = dadosDeAssinatura.getProperty("keyAlias")
        keyPassword = dadosDeAssinatura.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (temAssinaturaDePublicacao) {
        signingConfigs.getByName("release")
      } else {
        logger.warn(
          "AVISO: keystore.properties nao encontrado. O release vai sair com a " +
          "chave de depuracao e a Play Store NAO aceita. Veja keystore.properties.exemplo."
        )
        signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    /**
     * SEM ISTO O APP QUEBRA NO ANDROID 7.
     *
     * O codigo usa java.time (LocalDate, LocalTime, ZoneId, DateTimeFormatter,
     * Duration) em 6 arquivos -- inclusive no init da MainViewModel e no
     * BootCompleteReceiver. Essas classes so existem a partir do Android 8
     * (API 26), mas o minSdk e 24: a Play Store ofereceria o app para Android
     * 7.0 e 7.1, onde ele estouraria NoClassDefFoundError logo na abertura.
     * O lint apontava 126 erros de NewApi por causa disso.
     *
     * O desugaring da biblioteca padrao embute uma implementacao dessas classes
     * no proprio APK. Com ele, o java.time funciona ate no Android 7 e o
     * alcance do app continua sendo o minSdk 24.
     */
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }

  // Onde o Room grava o retrato de cada versao do banco. Ver o comentario do
  // exportSchema em AppDatabase: sem isto as migracoes das versoes 1 a 5 se
  // perderam e nao ha como reescreve-las.
  ksp { arg("room.schemaLocation", "$projectDir/schemas") }
  lint {
    abortOnError = false
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
//
// REMOVIDO o bloco do Firebase e da pilha de rede (Retrofit, OkHttp, Moshi).
// Vieram no template do AI Studio e nao eram usados por nenhuma linha do app.
// Elas arrastavam INTERNET e ACCESS_NETWORK_STATE para o manifest final pela
// fusao de manifests, o que num app de lembrete de agua gera pergunta na
// revisao da Play Store e obriga a declarar coleta de dados no formulario de
// Seguranca dos Dados.
dependencies {
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.play.services.location)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}