plugins { `java-library` }
repositories { google(); mavenCentral() }
dependencies {
    implementation("com.android.tools.build:gradle-api:9.2.1")
    implementation("org.ow2.asm:asm:9.7.1")
    testImplementation("junit:junit:4.13.2")
}
