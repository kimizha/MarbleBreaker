import pathlib
import xml.etree.ElementTree as ET

for path in pathlib.Path("app/build/test-results").rglob("TEST-*.xml"):
    suite = ET.parse(path).getroot()
    print("UNIT TESTS:", suite.attrib)
    for case in suite.findall("testcase"):
        for failure in case.findall("failure"):
            print("FAIL:", case.attrib, failure.text)
lint = pathlib.Path("app/build/reports/lint-results-debug.txt")
if lint.exists():
    print(lint.read_text())
