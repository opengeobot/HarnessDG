# compose 栈功能验证脚本（经 nginx 8081 入口）
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081'
$pass = 0; $fail = 0
function Check($name, $cond) {
  if ($cond) { $script:pass++; Write-Host "[PASS] $name" }
  else       { $script:fail++; Write-Host "[FAIL] $name" }
}

# 1. 健康
$h = Invoke-RestMethod "$base/actuator/health" -TimeoutSec 10
Check '健康检查 UP' ($h.status -eq 'UP')

# 2. 市场 options（匿名）
$opt = (Invoke-RestMethod "$base/api/v1/metadata/options" -TimeoutSec 10).data
$names = $opt.taxonomies.PSObject.Properties.Name
Check 'options 恰含 9 个市场字典（无管理字典）' ($names.Count -eq 9 -and $names -notcontains 'sys_user_status')
$fw = @($opt.taxonomies.framework)
$pt = $fw | Where-Object { $_.key -eq 'pytorch' }
Check 'framework 含 pytorch 且 active' ($pt -and $pt.status -eq 'active')
$mt = @($opt.taxonomies.model_task)
$roots = @($mt | Where-Object { -not $_.parentKey })
$kids = @($mt | Where-Object { $_.parentKey })
Check "model_task 两级层级（根 $($roots.Count) / 子 $($kids.Count)）" ($roots.Count -ge 4 -and $kids.Count -ge 50)
$tg = $mt | Where-Object { $_.key -eq 'text-generation' }
Check 'text-generation 父项为 nlp' ($tg.parentKey -eq 'nlp')

# 3. 登录 + permissions
$login = Invoke-RestMethod -Method Post "$base/api/v1/auth/login" -ContentType 'application/json' `
  -Body '{"username":"platform-root","password":"Boot-Strap-1x"}' -TimeoutSec 10
$tok = $login.data.accessToken
Check '登录成功' ($tok -ne $null)
$hd = @{ Authorization = "Bearer $tok" }
$me = (Invoke-RestMethod "$base/api/v1/auth/me" -Headers $hd -TimeoutSec 10).data
Check 'me 含 permissions 数组（>=12 点）' ($me.permissions -and @($me.permissions).Count -ge 12)
Check 'me permissions 含 admin:menu:manage' (@($me.permissions) -contains 'admin:menu:manage')

# 4. /me/menus（接口驱动菜单）
$menus = (Invoke-RestMethod "$base/api/v1/me/menus" -Headers $hd -TimeoutSec 10).data.items
$codes = @($menus | ForEach-Object { $_.code })
Check "/me/menus 返回 $($codes.Count) 个菜单（>=8）" ($codes.Count -ge 8)
Check '含 admin_menus 且不含 directory(admin_console)' (($codes -contains 'admin_menus') -and ($codes -notcontains 'admin_console'))

# 5. 管理面：字典列表（9 市场 + sys_user_status）
$dicts = (Invoke-RestMethod "$base/api/v1/admin/dicts" -Headers $hd -TimeoutSec 10).data.items
Check "admin/dicts 共 $($dicts.Count) 个（>=10）" (@($dicts).Count -ge 10)
Check '含 sys_user_status 与 model_task' ((@($dicts.dictCode) -contains 'sys_user_status') -and (@($dicts.dictCode) -contains 'model_task'))

# 6. 管理面：菜单列表（种子树）
$am = (Invoke-RestMethod "$base/api/v1/admin/menus" -Headers $hd -TimeoutSec 10).data.items
Check "admin/menus 共 $(@($am).Count) 个（种子 9：1 目录+8 菜单）" (@($am).Count -eq 9)
Check '种子含 admin_console 目录' (@($am | Where-Object { $_.code -eq 'admin_console' -and $_.menuType -eq 'directory' }).Count -eq 1)

# 7. V18 迁移效果：仓库按迁移后的字典值过滤
$repos = (Invoke-RestMethod "$base/api/v1/repositories?type=model&task=text-generation" -TimeoutSec 10).data
Check "repositories?task=text-generation 可检索（total=$($repos.total)）" ($repos.total -ge 1)

# 8. 普通注册用户无权限时菜单为空集（用已禁用用户之外的新用户注册验证鉴权链路）
$uname = 'vfy' + (Get-Random -Maximum 99999)
$reg = Invoke-RestMethod -Method Post "$base/api/v1/auth/register" -ContentType 'application/json' `
  -Body (@{ username = $uname; nickname = 'verify'; password = 'Verify-Comp-1x' } | ConvertTo-Json) -TimeoutSec 10
$utok = $reg.data.accessToken
$um = (Invoke-RestMethod "$base/api/v1/me/menus" -Headers @{ Authorization = "Bearer $utok" } -TimeoutSec 10).data.items
Check "普通用户 $($uname) 菜单为空集" (@($um).Count -eq 0)

Write-Host "`n=== 结果：PASS $pass / FAIL $fail ==="
exit $fail
