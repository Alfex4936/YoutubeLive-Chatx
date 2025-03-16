# Define variables
$BUILD_DIR = "build/libs"
$JAR_NAME = "csw-app.jar"
$MAIN_CLASS = "csw.youtube.chat.Application"
$JAVA_OPTS = "-Xms2G -Xmx8G -XX:+UseZGC -XX:+ZGenerational -XX:TieredStopAtLevel=1 -Dspring.profiles.active=prod"

Get-Process | Where-Object { $_.Path -like "*csw-app.jar*" } | Stop-Process -Force
Remove-Item -Recurse -Force "D:\Dev\Java\youtube.chat\build" -ErrorAction SilentlyContinue

java -version

Write-Host "🛠️ Cleaning and building optimized JAR..."
# Run Gradle build
./gradlew clean bootJar --no-daemon --parallel --warning-mode all

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed!"
    exit 1
}

if (!(Test-Path "$BUILD_DIR/$JAR_NAME")) {
    Write-Host "❌ Build failed! JAR not found."
    exit 1
}

Write-Host "✅ Build complete! Running the application..."

# Run the JAR with optimizations
Start-Process -NoNewWindow -FilePath "java" -ArgumentList "$JAVA_OPTS -jar $BUILD_DIR/$JAR_NAME"
