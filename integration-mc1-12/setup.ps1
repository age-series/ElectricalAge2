param (
	[string]$branch = "forge_1.12.2"
)

if(Test-Path -Path UniversalModCore ){
		Remove-Item -Recurse -Force ./UniversalModCore
}

git clone --branch $branch https://github.com/TeamOpenIndustry/UniversalModCore.git

./UniversalModCore/template/setup.ps1 $branch eln2 "Electrical Age 2" 0.1.0 org.eln2.mc.ElnMain
