param(
    [Parameter(Mandatory)][ValidateSet("shot", "pid")][string]$Action,
    [string]$Out,
    [string]$Match = (Resolve-Path "$PSScriptRoot\..").Path
)
# Framework file (minecraft-multiloader-mods skill, assets/template/tools/mcwindow.ps1).
# Works on this mod's test client window only: the java process whose command line contains $Match (the repo path),
# so a game the user is playing is never captured or closed.
# "shot" copies the window's contents with PrintWindow (no focus, works behind other windows). "pid" prints its id.
# Input is not sent from here: 26.x reads input via SDL3, which ignores keys posted to an unfocused window, so the
# harness drives the client through the dev-only test driver mod (src/testdriver) instead.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class McWin {
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
$proc = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.CommandLine -like "*$Match*" } |
    ForEach-Object { Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue } |
    Where-Object { $_.MainWindowTitle -like "Minecraft*" } | Select-Object -First 1
if (-not $proc) { Write-Output "no test client window"; exit 1 }
if ($Action -eq "pid") { Write-Output $proc.Id; exit 0 }

$h = $proc.MainWindowHandle
Add-Type -AssemblyName System.Drawing
if ([McWin]::IsIconic($h)) { [McWin]::ShowWindow($h, 4) | Out-Null; Start-Sleep -Milliseconds 1500 }  # restore without activating
$r = New-Object McWin+RECT
[McWin]::GetClientRect($h, [ref]$r) | Out-Null
$bmp = New-Object System.Drawing.Bitmap $r.Right, $r.Bottom
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
$ok = [McWin]::PrintWindow($h, $hdc, 3)  # PW_CLIENTONLY | PW_RENDERFULLCONTENT
$g.ReleaseHdc($hdc)
$sum = 0
for ($x = 0; $x -lt $bmp.Width; $x += 16) { for ($y = 0; $y -lt $bmp.Height; $y += 16) { $c = $bmp.GetPixel($x, $y); $sum += $c.R + $c.G + $c.B } }
if (-not $ok -or $sum -eq 0) { Write-Output "capture failed (blank)"; exit 1 }
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Output "saved $Out ($($r.Right)x$($r.Bottom))"
