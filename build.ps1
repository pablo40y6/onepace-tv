param(
    [string]$Website = '',
    [string]$Toolchain = (Join-Path $env:TEMP 'OnePaceTV-AndroidToolchain-20261123'),
    [string]$Apk = (Join-Path (Split-Path -Parent $PSScriptRoot) 'OnePaceTV.apk')
)

$ErrorActionPreference = 'Stop'

function Invoke-Tool {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable returned $LASTEXITCODE" }
}

$jdk = (Get-ChildItem -LiteralPath (Join-Path $Toolchain 'jdk') -Directory | Select-Object -First 1).FullName
$tools = (Get-ChildItem -LiteralPath (Join-Path $Toolchain 'build-tools') -Directory | Select-Object -First 1).FullName
$androidJar = (Get-ChildItem -LiteralPath (Join-Path $Toolchain 'platform') -Recurse -File -Filter 'android.jar' | Select-Object -First 1).FullName
$java = Join-Path $jdk 'bin/java.exe'
$javac = Join-Path $jdk 'bin/javac.exe'
$jar = Join-Path $jdk 'bin/jar.exe'
$keytool = Join-Path $jdk 'bin/keytool.exe'
$aapt = Join-Path $tools 'aapt2.exe'
$zipalign = Join-Path $tools 'zipalign.exe'
$d8 = Join-Path $tools 'lib/d8.jar'
$signer = Join-Path $tools 'lib/apksigner.jar'

$work = Join-Path $Toolchain ('work-' + [guid]::NewGuid().ToString('N'))
$assets = Join-Path $work 'assets'
$res = Join-Path $work 'res'
$drawable = Join-Path $res 'drawable-nodpi'
$resourceSources = Join-Path $work 'generated'
$classes = Join-Path $work 'classes'
$dex = Join-Path $work 'dex'
foreach ($directory in @($assets, $drawable, $resourceSources, $classes, $dex)) {
    [void][System.IO.Directory]::CreateDirectory($directory)
}

$catalogSource = if ($Website) { $Website } else { '-' }
Invoke-Tool 'node' @((Join-Path $PSScriptRoot 'prepare-assets.cjs'), $catalogSource, $assets, $drawable)
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'res/drawable-nodpi/app_icon.png') -Destination (Join-Path $drawable 'app_icon.png')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'art/language-deck.png') -Destination (Join-Path $drawable 'language_deck.png')

Add-Type -AssemblyName System.Drawing
foreach ($cover in (Get-ChildItem -LiteralPath $drawable -Filter 'arc_*' -File)) {
    $sourceArt = [System.Drawing.Image]::FromFile($cover.FullName)
    $thumbnail = [System.Drawing.Bitmap]::new(320, 180)
    $canvas = [System.Drawing.Graphics]::FromImage($thumbnail)
    try {
        $canvas.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $width = [int][Math]::Min($sourceArt.Width, $sourceArt.Height * 16.0 / 9)
        $height = [int][Math]::Min($sourceArt.Height, $sourceArt.Width * 9.0 / 16)
        $left = [int](($sourceArt.Width - $width) / 2)
        $top = [int](($sourceArt.Height - $height) / 2)
        $destination = [System.Drawing.Rectangle]::new(0, 0, 320, 180)
        $canvas.DrawImage($sourceArt, $destination, $left, $top, $width, $height, [System.Drawing.GraphicsUnit]::Pixel)
        $thumbnail.Save((Join-Path $drawable ($cover.BaseName + '_thumb.jpg')), [System.Drawing.Imaging.ImageFormat]::Jpeg)
    } finally {
        foreach ($item in @($canvas, $thumbnail, $sourceArt)) { $item.Dispose() }
    }
}

$banner = [System.Drawing.Bitmap]::new(320, 180)
$drawing = [System.Drawing.Graphics]::FromImage($banner)
$canvasRect = [System.Drawing.Rectangle]::new(0, 0, 320, 180)
$sea = [System.Drawing.Drawing2D.LinearGradientBrush]::new($canvasRect,
    [System.Drawing.Color]::FromArgb(9, 23, 41), [System.Drawing.Color]::FromArgb(23, 61, 105),
    [System.Drawing.Drawing2D.LinearGradientMode]::Horizontal)
$ink = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(248, 235, 210))
$accent = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 187, 72))
$compass = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(112, 247, 193, 100), 1.25)
$title = [System.Drawing.Font]::new('Segoe UI', 42, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$subtitle = [System.Drawing.Font]::new('Segoe UI', 21, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
try {
    $drawing.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $drawing.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $drawing.FillRectangle($sea, $canvasRect)
    $drawing.FillRectangle($accent, 0, 0, 7, 180)
    $drawing.DrawEllipse($compass, 214, 39, 104, 104)
    $drawing.DrawEllipse($compass, 222, 47, 88, 88)
    for ($bearing = 0; $bearing -lt 360; $bearing += 30) {
        $radians = $bearing * [Math]::PI / 180
        $dx = [Math]::Cos($radians)
        $dy = [Math]::Sin($radians)
        $drawing.DrawLine($compass, [float](266 + 39 * $dx), [float](91 + 39 * $dy),
            [float](266 + 51 * $dx), [float](91 + 51 * $dy))
    }
    $drawing.DrawLine($compass, 266, 58, 266, 124)
    $drawing.DrawLine($compass, 235, 91, 297, 91)
    $drawing.DrawString('ONE PACE', $title, $ink, 25, 57)
    $drawing.FillRectangle($accent, 30, 123, 18, 2)
    $drawing.DrawString('TV', $subtitle, $accent, 54, 108)
    $banner.Save((Join-Path $drawable 'tv_banner.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    foreach ($item in @($subtitle, $title, $compass, $accent, $ink, $sea, $drawing, $banner)) { $item.Dispose() }
}

$compiled = Join-Path $work 'compiled.zip'
$unsigned = Join-Path $work 'unsigned.apk'
$aligned = Join-Path $work 'aligned.apk'
Invoke-Tool $aapt @('compile', '--dir', $res, '-o', $compiled)
Invoke-Tool $aapt @('link', '-o', $unsigned, '-I', $androidJar, '--manifest', (Join-Path $PSScriptRoot 'AndroidManifest.xml'), '--java', $resourceSources, '-A', $assets, $compiled)

$source = @(Get-ChildItem -LiteralPath @((Join-Path $PSScriptRoot 'src'), $resourceSources) -Filter '*.java' -Recurse -File | Select-Object -ExpandProperty FullName)
$bytecode = Join-Path $work 'classes.jar'
Invoke-Tool $javac (@('--release', '8', '-encoding', 'UTF-8', '-classpath', $androidJar, '-d', $classes) + $source)
Invoke-Tool $jar @('--create', '--file', $bytecode, '-C', $classes, '.')
Invoke-Tool $java @('-cp', $d8, 'com.android.tools.r8.D8', '--min-api', '26', '--lib', $androidJar, '--output', $dex, $bytecode)

Invoke-Tool $jar @('--update', '--file', $unsigned, '-C', $dex, 'classes.dex')
Invoke-Tool $zipalign @('-f', '4', $unsigned, $aligned)

# Local test signing key. It is never included in the APK or the source directory.
$keystore = Join-Path $Toolchain 'onepace-tv-test.p12'
if (-not (Test-Path -LiteralPath $keystore)) {
    Invoke-Tool $keytool @('-genkeypair', '-keystore', $keystore, '-storetype', 'PKCS12', '-storepass', 'android',
        '-alias', 'onepace-tv-test', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
        '-dname', 'CN=One Pace TV Test', '-noprompt')
}
Invoke-Tool $java @('-jar', $signer, 'sign', '--ks', $keystore, '--ks-key-alias', 'onepace-tv-test',
    '--ks-pass', 'pass:android', '--v4-signing-enabled', 'false', '--out', $Apk, $aligned)
Invoke-Tool $java @('-jar', $signer, 'verify', '--verbose', $Apk)
Invoke-Tool $zipalign @('-c', '4', $Apk)
Get-Item -LiteralPath $Apk | Select-Object FullName, Length
