param([Parameter(Mandatory=$true)][string]$LayoutPath,[Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$layouts = Get-Content -LiteralPath $LayoutPath -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($layout in $layouts) {
    $xs = @(40)
    foreach ($w in $layout.widths) { $xs += ($xs[-1] + $w) }
    $ys = @(30)
    foreach ($h in $layout.heights) { $ys += ($ys[-1] + $h) }
    $bitmap = New-Object System.Drawing.Bitmap ([int]($xs[-1]+20)), ([int]($ys[-1]+20))
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.Clear([System.Drawing.Color]::White)
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $grid = New-Object System.Drawing.Pen ([System.Drawing.ColorTranslator]::FromHtml('#DCE5DF'))
    foreach ($x in $xs) { $g.DrawLine($grid,[int]$x,30,[int]$x,[int]$ys[-1]) }
    foreach ($y in $ys) { $g.DrawLine($grid,40,[int]$y,[int]$xs[-1],[int]$y) }
    foreach ($cell in $layout.cells) {
        $x = $xs[$cell.col-1]; $y = $ys[$cell.row-1]
        $w = $xs[$cell.endCol] - $x; $h = $layout.heights[$cell.row-1]
        $bg = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#' + $cell.fill))
        $g.FillRectangle($bg,[single]($x+1),[single]($y+1),[single]($w-2),[single]($h-2))
        $style = if ($cell.bold) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }
        $font = New-Object System.Drawing.Font 'Calibri', ([single]$cell.fontSize), $style
        $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#' + $cell.color))
        $format = New-Object System.Drawing.StringFormat
        if ($cell.align -eq 'right') { $format.Alignment = [System.Drawing.StringAlignment]::Far }
        elseif ($cell.align -eq 'center') { $format.Alignment = [System.Drawing.StringAlignment]::Center }
        $rect = New-Object System.Drawing.RectangleF ([single]($x+3)),([single]($y+2)),([single]($w-6)),([single]($h-4))
        $measured = $g.MeasureString([string]$cell.text,$font,[int]($w-6),$format)
        if ($measured.Height -gt $h-3) { throw ('Clipped text: ' + $layout.name + ' R' + $cell.row + 'C' + $cell.col + ' needs ' + $measured.Height + 'px; row has ' + $h + 'px') }
        $g.DrawString([string]$cell.text,$font,$brush,$rect,$format)
        $font.Dispose(); $brush.Dispose(); $bg.Dispose(); $format.Dispose()
    }
    $target = Join-Path $OutputDirectory ($layout.name + '.png')
    if (Test-Path -LiteralPath $target) { throw 'Preview already exists; refusing overwrite' }
    $bitmap.Save((Join-Path $OutputDirectory ($layout.name + '.png')),[System.Drawing.Imaging.ImageFormat]::Png)
    $grid.Dispose(); $g.Dispose(); $bitmap.Dispose()
    Write-Output ($layout.name + ': Calibri text-fit check and approximate preview PASS')
}
