Add-Type -AssemblyName System.Drawing

$root = if ($env:NOVA_ASSET_ROOT) { $env:NOVA_ASSET_ROOT } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$res = Join-Path $root 'app/src/main/res'

function New-Glyphs([string]$text, [float]$size, [System.Drawing.FontStyle]$style) {
  $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
  $path.AddString($text, [System.Drawing.FontFamily]::new('Times New Roman'), [int]$style,
    $size, [System.Drawing.PointF]::Empty, [System.Drawing.StringFormat]::GenericTypographic)
  $matrix = [System.Drawing.Drawing2D.Matrix]::new()
  $matrix.Scale(1.24, 1)
  $path.Transform($matrix)
  $matrix.Dispose()
  return $path
}

function Paint-Glyphs($graphics, $path, [float]$left, [float]$top) {
  $bounds = $path.GetBounds()
  $matrix = [System.Drawing.Drawing2D.Matrix]::new()
  $matrix.Translate($left - $bounds.Left, $top - $bounds.Top)
  $path.Transform($matrix)
  $graphics.FillPath([System.Drawing.Brushes]::White, $path)
  $matrix.Dispose()
  $path.Dispose()
}

function New-LogoBitmap([int]$width, [int]$height, [bool]$foregroundOnly = $false) {
  $bmp = New-Object System.Drawing.Bitmap($width, $height)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
  if (-not $foregroundOnly) {
    $blue = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#2046B6'))
    $purple = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#5A3BC1'))
    $teal = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#007D89'))
    $g.FillRectangle($blue, 0, 0, $width, $height)
    $g.FillPolygon($purple, [System.Drawing.PointF[]]@([System.Drawing.PointF]::new(0,0),[System.Drawing.PointF]::new($width*0.775,0),[System.Drawing.PointF]::new($width*0.2125,$height),[System.Drawing.PointF]::new(0,$height)))
    $g.FillPolygon($teal, [System.Drawing.PointF[]]@([System.Drawing.PointF]::new($width,$height*0.089),[System.Drawing.PointF]::new($width,$height),[System.Drawing.PointF]::new($width*0.4875,$height)))
    $blue.Dispose()
    $purple.Dispose()
    $teal.Dispose()
  }
  $scale = if ($foregroundOnly) { $width / 1580.0 } else { [Math]::Min($width / 970.0, $height / 450.0) }
  $g.TranslateTransform([float]($width/2), [float]($height/2))
  $g.ScaleTransform($scale, $scale)
  $cap = New-Glyphs 'N' 276 Bold
  $tail = New-Glyphs 'ova' 226 Bold
  $capBox = $cap.GetBounds()
  $tailBox = $tail.GetBounds()
  $left = -($capBox.Width + 13 + $tailBox.Width)/2
  $tailX = $left + $capBox.Width + 13
  Paint-Glyphs $g $cap $left (95 - $capBox.Height)
  Paint-Glyphs $g $tail $tailX (95 - $tailBox.Height)
  $letterX = $tailX + 23
  foreach ($letter in 'h','o','m','e') {
    $glyph = New-Glyphs $letter 74 Regular
    $box = $glyph.GetBounds()
    Paint-Glyphs $g $glyph $letterX (-33 - $box.Height)
    $letterX += $box.Width + 23
  }
  $cx = [float]($tailX + $tailBox.Width + 40)
  $cy = -79
  $r = 45
  $ri = $r * 0.42
  $points = New-Object System.Drawing.PointF[] 8
  for ($i=0; $i -lt 8; $i++) { $a = -[Math]::PI/2 + $i*[Math]::PI/4; $rr = if (($i % 2) -eq 0) { $r } else { $ri }; $points[$i] = [System.Drawing.PointF]::new($cx + [float]([Math]::Cos($a)*$rr), $cy + [float]([Math]::Sin($a)*$rr)) }
  $g.FillPolygon([System.Drawing.Brushes]::White, $points)
  $g.Dispose()
  return $bmp
}

$master = New-LogoBitmap 1024 1024
$densitySizes = @{'mipmap-mdpi'=48;'mipmap-hdpi'=72;'mipmap-xhdpi'=96;'mipmap-xxhdpi'=144;'mipmap-xxxhdpi'=192}
foreach ($entry in $densitySizes.GetEnumerator()) {
  $dir = Join-Path $res $entry.Key
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $out = New-Object System.Drawing.Bitmap($entry.Value, $entry.Value)
  $gg = [System.Drawing.Graphics]::FromImage($out)
  $gg.DrawImage($master, 0, 0, $entry.Value, $entry.Value)
  $gg.Dispose(); $out.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png); $out.Dispose()
}
$master.Dispose()
$foregroundDir = Join-Path $res 'drawable-nodpi'
New-Item -ItemType Directory -Force -Path $foregroundDir | Out-Null
$foreground = New-LogoBitmap 432 432 $true
$foreground.Save((Join-Path $foregroundDir 'nova_brand_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$foreground.Dispose()
$drawableDir = Join-Path $res 'drawable'
New-Item -ItemType Directory -Force -Path $drawableDir | Out-Null
$banner = New-LogoBitmap 320 180
$banner.Save((Join-Path $drawableDir 'tv_banner.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$banner.Dispose()
