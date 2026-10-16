# Loads demo data through the public API: products, goods receipts, sales and the ABC analysis.
# Usage (with the stack running): powershell -ExecutionPolicy Bypass -File docs/demo-data.ps1
$ErrorActionPreference = "Stop"
$api = "http://localhost:8080"

function Call($method, $path, $body) {
  $params = @{ UseBasicParsing = $true; Method = $method; Uri = "$api$path" }
  if ($body) {
    $params.ContentType = "application/json"
    $params.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 6))
  }
  Invoke-RestMethod @params
}
function Day($n) { (Get-Date).AddDays($n).ToString("yyyy-MM-dd") }
function Receive($sku, $batch, $qty, $cost, $expiry) {
  $key = "35" + (-join (1..42 | ForEach-Object { Get-Random -Minimum 0 -Maximum 10 }))
  $item = @{ sku = $sku; batchCode = $batch; quantity = $qty; unitCost = $cost; manufacturingDate = $null; expiryDate = $expiry }
  Call POST "/api/v1/stock/receipts/nfe" @{ accessKey = $key; number = "1"; items = @($item) } | Out-Null
  Write-Host "  received $qty x $sku ($batch)"
}
function Sell($ref, $items) {
  Call POST "/api/v1/sales" @{ idempotencyKey = [guid]::NewGuid().ToString(); orderRef = $ref; items = @($items) } | Out-Null
  Write-Host "  sale $ref"
}

$products = @(
  @{ sku = "NB-LENOVO-E14";    description = "Lenovo ThinkPad E14 Notebook"; category = "Computers";       unit = "UN";  minimumStock = 3;  salePrice = 4890.00; controlsExpiry = $false },
  @{ sku = "MON-DELL-P2422H";  description = "Dell P2422H 24in Monitor";     category = "Computers";       unit = "UN";  minimumStock = 4;  salePrice = 1290.00; controlsExpiry = $false },
  @{ sku = "KEY-LOGI-K120";    description = "Logitech K120 Keyboard";       category = "Peripherals";     unit = "UN";  minimumStock = 10; salePrice = 89.90;   controlsExpiry = $false },
  @{ sku = "MOU-LOGI-M170";    description = "Logitech M170 Wireless Mouse"; category = "Peripherals";     unit = "UN";  minimumStock = 10; salePrice = 79.90;   controlsExpiry = $false },
  @{ sku = "SW-TPLINK-24P";    description = "TP-Link 24-port Switch";       category = "Networking";      unit = "UN";  minimumStock = 2;  salePrice = 1450.00; controlsExpiry = $false },
  @{ sku = "CAB-HDMI-2M";      description = "HDMI Cable 2m";                category = "Networking";      unit = "UN";  minimumStock = 15; salePrice = 39.90;   controlsExpiry = $false },
  @{ sku = "INK-EPS-664-BK";   description = "Epson 664 Black Ink Refill";   category = "IT Supplies";     unit = "UN";  minimumStock = 12; salePrice = 54.90;   controlsExpiry = $true },
  @{ sku = "TONER-BRO-TN1060"; description = "Brother TN-1060 Toner";        category = "IT Supplies";     unit = "UN";  minimumStock = 6;  salePrice = 159.90;  controlsExpiry = $true },
  @{ sku = "ALCOHOL-IPA-1L";   description = "Isopropyl Alcohol 1L";         category = "IT Supplies";     unit = "UN";  minimumStock = 8;  salePrice = 38.00;   controlsExpiry = $true },
  @{ sku = "PAPER-A4-500";     description = "A4 Paper 75g (500 sheets)";    category = "Office Supplies"; unit = "PKG"; minimumStock = 40; salePrice = 29.90;   controlsExpiry = $false }
)

$current = @(Call GET "/api/v1/products" $null).sku
if ($products | Where-Object { $current -contains $_.sku }) { throw "Demo data is already loaded." }

Write-Host "Creating products"
foreach ($p in $products) { Call POST "/api/v1/products" $p | Out-Null; Write-Host "  $($p.sku)" }

Write-Host "Receiving goods"
Receive "NB-LENOVO-E14"     "LOT-001" 8   3200 $null
Receive "MON-DELL-P2422H"   "LOT-001" 20  850  $null
Receive "KEY-LOGI-K120"     "LOT-001" 40  55   $null
Receive "MOU-LOGI-M170"     "LOT-001" 35  48   $null
Receive "SW-TPLINK-24P"     "LOT-001" 6   980  $null
Receive "CAB-HDMI-2M"       "LOT-001" 30  22   $null
Receive "PAPER-A4-500"      "LOT-001" 120 18   $null
Receive "INK-EPS-664-BK"    "T-2601"  20  34   (Day 120)
Receive "INK-EPS-664-BK"    "T-2602"  30  34   (Day 300)
Receive "TONER-BRO-TN1060"  "B-2601"  8   98   (Day 200)
Receive "TONER-BRO-TN1060"  "B-2602"  10  98   (Day 400)
Receive "ALCOHOL-IPA-1L"    "A-2601"  12  21   (Day 45)
Receive "ALCOHOL-IPA-1L"    "A-2602"  24  21   (Day 200)

Write-Host "Registering sales"
Sell "DEMO-001" @(@{ sku = "NB-LENOVO-E14"; quantity = 4; unitPrice = 4890 }, @{ sku = "MON-DELL-P2422H"; quantity = 6; unitPrice = 1290 })
Sell "DEMO-002" @(@{ sku = "NB-LENOVO-E14"; quantity = 2; unitPrice = 4890 }, @{ sku = "SW-TPLINK-24P"; quantity = 4; unitPrice = 1450 }, @{ sku = "KEY-LOGI-K120"; quantity = 10; unitPrice = 89.9 })
Sell "DEMO-003" @(@{ sku = "MON-DELL-P2422H"; quantity = 4; unitPrice = 1290 }, @{ sku = "MOU-LOGI-M170"; quantity = 10; unitPrice = 79.9 }, @{ sku = "CAB-HDMI-2M"; quantity = 5; unitPrice = 39.9 })
Sell "DEMO-004" @(@{ sku = "TONER-BRO-TN1060"; quantity = 8; unitPrice = 159.9 }, @{ sku = "INK-EPS-664-BK"; quantity = 12; unitPrice = 54.9 })
Sell "DEMO-005" @(@{ sku = "ALCOHOL-IPA-1L"; quantity = 6; unitPrice = 38 }, @{ sku = "PAPER-A4-500"; quantity = 20; unitPrice = 29.9 })

Write-Host "Recalculating ABC analysis"
Call POST "/api/v1/stock/abc-analysis/recalculate" $null | Out-Null
Write-Host "Done."
