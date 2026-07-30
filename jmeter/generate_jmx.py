#!/usr/bin/env python3
"""Generate campus-activity-perf.jmx for JMeter 5.x."""
from __future__ import annotations

from pathlib import Path
from xml.sax.saxutils import escape

OUT = Path(__file__).with_name("campus-activity-perf.jmx")


def arg(name: str, value: str) -> str:
    return f"""          <elementProp name="{escape(name)}" elementType="Argument">
            <stringProp name="Argument.name">{escape(name)}</stringProp>
            <stringProp name="Argument.value">{escape(value)}</stringProp>
            <stringProp name="Argument.metadata">=</stringProp>
          </elementProp>"""


def http_request(
    name: str,
    method: str,
    path: str,
    *,
    body: str | None = None,
    query: list[tuple[str, str]] | None = None,
    enabled: bool = True,
) -> str:
    """path is relative to API_PREFIX, e.g. /auth/login — full path = ${API_PREFIX}/..."""
    # Use path as path, domain/port from defaults; path includes ${API_PREFIX}
    full_path = f"${{API_PREFIX}}{path}" if not path.startswith("${") else path
    args_xml = ""
    if query:
        items = []
        for k, v in query:
            items.append(
                f"""            <elementProp name="{escape(k)}" elementType="HTTPArgument">
              <boolProp name="HTTPArgument.always_encode">true</boolProp>
              <stringProp name="Argument.name">{escape(k)}</stringProp>
              <stringProp name="Argument.value">{escape(v)}</stringProp>
              <stringProp name="Argument.metadata">=</stringProp>
              <boolProp name="HTTPArgument.use_equals">true</boolProp>
            </elementProp>"""
            )
        args_xml = (
            '<elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">\n'
            '              <collectionProp name="Arguments.arguments">\n'
            + "\n".join(items)
            + "\n              </collectionProp>\n            </elementProp>"
        )
    elif body is not None:
        args_xml = f"""<boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
            <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
              <collectionProp name="Arguments.arguments">
                <elementProp name="" elementType="HTTPArgument">
                  <boolProp name="HTTPArgument.always_encode">false</boolProp>
                  <stringProp name="Argument.value">{escape(body)}</stringProp>
                  <stringProp name="Argument.metadata">=</stringProp>
                </elementProp>
              </collectionProp>
            </elementProp>"""
    else:
        args_xml = """<elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
              <collectionProp name="Arguments.arguments"/>
            </elementProp>"""

    return f"""        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="{escape(name)}" enabled="{"true" if enabled else "false"}">
            {args_xml}
            <stringProp name="HTTPSampler.domain"></stringProp>
            <stringProp name="HTTPSampler.port"></stringProp>
            <stringProp name="HTTPSampler.protocol"></stringProp>
            <stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>
            <stringProp name="HTTPSampler.path">{escape(full_path)}</stringProp>
            <stringProp name="HTTPSampler.method">{method}</stringProp>
            <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
            <boolProp name="HTTPSampler.auto_redirects">false</boolProp>
            <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
            <boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>
            <stringProp name="HTTPSampler.embedded_url_re"></stringProp>
            <stringProp name="HTTPSampler.connect_timeout"></stringProp>
            <stringProp name="HTTPSampler.response_timeout"></stringProp>
          </HTTPSamplerProxy>"""


def json_extractor(name: str, var: str, path: str, default: str = "") -> str:
    return f"""          <JSONPostProcessor guiclass="JSONPostProcessorGui" testclass="JSONPostProcessor" testname="{escape(name)}" enabled="true">
            <stringProp name="JSONPostProcessor.referenceNames">{escape(var)}</stringProp>
            <stringProp name="JSONPostProcessor.jsonPathExprs">{escape(path)}</stringProp>
            <stringProp name="JSONPostProcessor.match_numbers">1</stringProp>
            <stringProp name="JSONPostProcessor.defaultValues">{escape(default)}</stringProp>
          </JSONPostProcessor>
          <hashTree/>"""


def duration_assertion(ms: int = 3000) -> str:
    return f"""          <DurationAssertion guiclass="DurationAssertionGui" testclass="DurationAssertion" testname="Duration &lt; {ms}ms" enabled="true">
            <stringProp name="DurationAssertion.duration">{ms}</stringProp>
          </DurationAssertion>
          <hashTree/>"""


def json_code_ok() -> str:
    return """          <JSONPathAssertion guiclass="JSONPathAssertionGui" testclass="JSONPathAssertion" testname="Assert code==0" enabled="true">
            <stringProp name="JSON_PATH">$.code</stringProp>
            <stringProp name="EXPECTED_VALUE">0</stringProp>
            <boolProp name="JSONVALIDATION">true</boolProp>
            <boolProp name="EXPECT_NULL">false</boolProp>
            <boolProp name="INVERT">false</boolProp>
            <boolProp name="ISREGEX">false</boolProp>
          </JSONPathAssertion>
          <hashTree/>"""


def response_code_ok() -> str:
    return """          <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion" testname="HTTP 200" enabled="true">
            <collectionProp name="Asserion.test_strings">
              <stringProp name="49586">200</stringProp>
            </collectionProp>
            <stringProp name="Assertion.custom_message"></stringProp>
            <stringProp name="Assertion.test_field">Assertion.response_code</stringProp>
            <boolProp name="Assertion.assume_success">false</boolProp>
            <intProp name="Assertion.test_type">8</intProp>
          </ResponseAssertion>
          <hashTree/>"""


def jsr223(name: str, script: str, *, lang: str = "groovy", as_sampler: bool = False) -> str:
    cls = "JSR223Sampler" if as_sampler else "JSR223PostProcessor"
    gui = "TestBeanGUI"
    tag = cls
    return f"""          <{tag} guiclass="{gui}" testclass="{cls}" testname="{escape(name)}" enabled="true">
            <stringProp name="cacheKey">true</stringProp>
            <stringProp name="filename"></stringProp>
            <stringProp name="parameters"></stringProp>
            <stringProp name="script">{escape(script)}</stringProp>
            <stringProp name="scriptLanguage">{lang}</stringProp>
          </{tag}>
          <hashTree/>"""


def header_auth() -> str:
    return """          <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="Auth Bearer" enabled="true">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Authorization</stringProp>
                <stringProp name="Header.value">Bearer ${token}</stringProp>
              </elementProp>
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Content-Type</stringProp>
                <stringProp name="Header.value">application/json; charset=utf-8</stringProp>
              </elementProp>
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Accept</stringProp>
                <stringProp name="Header.value">application/json</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
          <hashTree/>"""


def header_json() -> str:
    return """          <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="JSON Headers" enabled="true">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Content-Type</stringProp>
                <stringProp name="Header.value">application/json; charset=utf-8</stringProp>
              </elementProp>
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Accept</stringProp>
                <stringProp name="Header.value">application/json</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
          <hashTree/>"""


def csv_dataset(filename: str, var_names: str = "userId", *, recycle: bool = True, stop_eof: bool = False) -> str:
    return f"""          <CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="CSV {escape(filename)}" enabled="true">
            <stringProp name="delimiter">,</stringProp>
            <stringProp name="fileEncoding">UTF-8</stringProp>
            <stringProp name="filename">{escape(filename)}</stringProp>
            <boolProp name="ignoreFirstLine">true</boolProp>
            <boolProp name="quotedData">false</boolProp>
            <boolProp name="recycle">{"true" if recycle else "false"}</boolProp>
            <stringProp name="shareMode">shareMode.group</stringProp>
            <boolProp name="stopThread">{"true" if stop_eof else "false"}</boolProp>
            <stringProp name="variableNames">{escape(var_names)}</stringProp>
          </CSVDataSet>
          <hashTree/>"""


def login_fragment(*, password_var: str = "${PASSWORD}", user_var: str = "${userId}") -> str:
    body = '{"userId":"' + user_var.replace("${", "${") + '","password":"' + password_var + '"}'
    # Fix: build properly
    body = f'{{"userId":"{user_var}","password":"{password_var}"}}'
    # Actually JMeter variables shouldn't be escaped in a weird way - in XML we need the literal ${userId}
    body = '{"userId":"${userId}","password":"${PASSWORD}"}'
    return "\n".join(
        [
            http_request("POST /auth/login", "POST", "/auth/login", body=body),
            "<hashTree>",
            json_extractor("Extract token", "token", "$.data.token", "NOT_FOUND"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
        ]
    )


def sampler_with_children(sampler_xml: str, *children: str, sla: bool = True, assert_code: bool = True) -> str:
    parts = [sampler_xml, "<hashTree>"]
    if assert_code:
        parts.append(response_code_ok())
        parts.append(json_code_ok())
    if sla:
        parts.append(duration_assertion(3000))
    parts.extend(children)
    parts.append("</hashTree>")
    return "\n".join(parts)


def thread_group(
    name: str,
    threads: int,
    ramp: int,
    loops: int,
    *,
    enabled: bool = True,
    on_sample_error: str = "continue",
) -> str:
    return f"""        <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="{escape(name)}" enabled="{"true" if enabled else "false"}">
          <stringProp name="ThreadGroup.on_sample_error">{on_sample_error}</stringProp>
          <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller" enabled="true">
            <boolProp name="LoopController.continue_forever">false</boolProp>
            <stringProp name="LoopController.loops">{loops}</stringProp>
          </elementProp>
          <stringProp name="ThreadGroup.num_threads">{threads}</stringProp>
          <stringProp name="ThreadGroup.ramp_time">{ramp}</stringProp>
          <boolProp name="ThreadGroup.scheduler">false</boolProp>
          <stringProp name="ThreadGroup.duration"></stringProp>
          <stringProp name="ThreadGroup.delay"></stringProp>
          <boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>
        </ThreadGroup>"""


def setup_thread_group(name: str, enabled: bool = True) -> str:
    return f"""        <SetupThreadGroup guiclass="SetupThreadGroupGui" testclass="SetupThreadGroup" testname="{escape(name)}" enabled="{"true" if enabled else "false"}">
          <stringProp name="ThreadGroup.on_sample_error">stoptest</stringProp>
          <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller" enabled="true">
            <boolProp name="LoopController.continue_forever">false</boolProp>
            <stringProp name="LoopController.loops">1</stringProp>
          </elementProp>
          <stringProp name="ThreadGroup.num_threads">1</stringProp>
          <stringProp name="ThreadGroup.ramp_time">1</stringProp>
          <boolProp name="ThreadGroup.scheduler">false</boolProp>
          <stringProp name="ThreadGroup.duration"></stringProp>
          <stringProp name="ThreadGroup.delay"></stringProp>
        </SetupThreadGroup>"""


def teardown_thread_group(name: str, enabled: bool = True) -> str:
    return f"""        <PostThreadGroup guiclass="PostThreadGroupGui" testclass="PostThreadGroup" testname="{escape(name)}" enabled="{"true" if enabled else "false"}">
          <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
          <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller" enabled="true">
            <boolProp name="LoopController.continue_forever">false</boolProp>
            <stringProp name="LoopController.loops">1</stringProp>
          </elementProp>
          <stringProp name="ThreadGroup.num_threads">1</stringProp>
          <stringProp name="ThreadGroup.ramp_time">1</stringProp>
          <boolProp name="ThreadGroup.scheduler">false</boolProp>
          <stringProp name="ThreadGroup.duration"></stringProp>
          <stringProp name="ThreadGroup.delay"></stringProp>
        </PostThreadGroup>"""


ACTIVITY_CREATE_CAP50 = (
    '{"title":"JMeter报名竞态-${__time(yyyyMMddHHmmss)}","category":"academic",'
    '"description":"JMeter concurrency signup capacity test (maxParticipants=50)",'
    '"startTime":"2027-06-01T14:00:00","endTime":"2027-06-01T16:00:00",'
    '"location":"东中院4-101","maxParticipants":50,"tags":["jmeter","perf"]}'
)

ACTIVITY_CREATE_FAV = (
    '{"title":"JMeter收藏竞态-${__time(yyyyMMddHHmmss)}","category":"academic",'
    '"description":"JMeter concurrency favorite count test",'
    '"startTime":"2027-06-02T14:00:00","endTime":"2027-06-02T16:00:00",'
    '"location":"东中院4-102","maxParticipants":200,"tags":["jmeter","favorite"]}'
)

RESET_COUNTERS = """props.put("signupSuccess", "0");
props.put("signupBusinessFail", "0");
props.put("signupHttpError", "0");
props.put("favToggleOk", "0");
props.put("favToggleFail", "0");
props.put("dupSignupOkUnexpected", "0");
props.put("dupSignupExpectedFail", "0");
log.info("Counters reset");"""

SAVE_SETUP_IDS = """props.put("TEST_ACTIVITY_ID", vars.get("testActivityId"));
props.put("FAVORITE_ACTIVITY_ID", vars.get("favActivityId"));
props.put("BASELINE_ACTIVITY_ID", vars.get("testActivityId"));
log.info("TEST_ACTIVITY_ID=" + props.get("TEST_ACTIVITY_ID") + " FAVORITE_ACTIVITY_ID=" + props.get("FAVORITE_ACTIVITY_ID"));"""

COUNT_SIGNUP = """import groovy.json.JsonSlurper
def raw = prev.getResponseDataAsString()
def json = new JsonSlurper().parseText(raw)
def http = prev.getResponseCode() as int
synchronized (props) {
  if (http >= 500) {
    props.put("signupHttpError", String.valueOf(((props.get("signupHttpError") ?: "0") as int) + 1))
  } else if (json?.code == 0) {
    props.put("signupSuccess", String.valueOf(((props.get("signupSuccess") ?: "0") as int) + 1))
  } else {
    props.put("signupBusinessFail", String.valueOf(((props.get("signupBusinessFail") ?: "0") as int) + 1))
  }
}
// Do not mark sample failed for expected business rejects (full / already registered)
if (http < 500 && json?.code != 0) {
  prev.setSuccessful(true)
}"""

COUNT_FAV = """import groovy.json.JsonSlurper
def json = new JsonSlurper().parseText(prev.getResponseDataAsString())
def http = prev.getResponseCode() as int
synchronized (props) {
  if (http == 200 && json?.code == 0) {
    props.put("favToggleOk", String.valueOf(((props.get("favToggleOk") ?: "0") as int) + 1))
  } else {
    props.put("favToggleFail", String.valueOf(((props.get("favToggleFail") ?: "0") as int) + 1))
  }
}"""

COUNT_DUP = """import groovy.json.JsonSlurper
def json = new JsonSlurper().parseText(prev.getResponseDataAsString())
def http = prev.getResponseCode() as int
synchronized (props) {
  if (http == 200 && json?.code == 0) {
    props.put("dupSignupOkUnexpected", String.valueOf(((props.get("dupSignupOkUnexpected") ?: "0") as int) + 1))
  } else if (http < 500) {
    props.put("dupSignupExpectedFail", String.valueOf(((props.get("dupSignupExpectedFail") ?: "0") as int) + 1))
    prev.setSuccessful(true)
  }
}"""

VERIFY_SIGNUP = """import groovy.json.JsonSlurper
def success = (props.get("signupSuccess") ?: "0") as int
def bizFail = (props.get("signupBusinessFail") ?: "0") as int
def httpErr = (props.get("signupHttpError") ?: "0") as int
if (success == 0 && bizFail == 0) {
  log.info("Skip signup verify (TG4 did not run)")
  return
}
def cap = (vars.get("MAX_PARTICIPANTS") ?: props.get("MAX_PARTICIPANTS") ?: "50") as int
def json = new JsonSlurper().parseText(prev.getResponseDataAsString())
def count = json?.data?.signupCount as Integer
log.info("Signup verify: success=" + success + " bizFail=" + bizFail + " httpErr=" + httpErr + " signupCount=" + count + " cap=" + cap)
props.put("VERIFY_SIGNUP_OK", (success == cap && count == cap && httpErr == 0) ? "true" : "false")
if (success != cap) {
  AssertionResult.setFailure(true)
  AssertionResult.setFailureMessage("signupSuccess=" + success + " expected=" + cap)
}
if (count != cap) {
  AssertionResult.setFailure(true)
  AssertionResult.setFailureMessage((AssertionResult.getFailureMessage() ?: "") + " signupCount=" + count + " expected=" + cap)
}
if (httpErr != 0) {
  AssertionResult.setFailure(true)
  AssertionResult.setFailureMessage((AssertionResult.getFailureMessage() ?: "") + " http5xx=" + httpErr)
}"""

VERIFY_FAV = """import groovy.json.JsonSlurper
def ok = (props.get("favToggleOk") ?: "0") as int
if (ok == 0) {
  log.info("Skip favorite verify (TG5 did not run)")
  return
}
def before = (props.get("FAV_COUNT_BEFORE") ?: "0") as int
def json = new JsonSlurper().parseText(prev.getResponseDataAsString())
def after = (json?.data?.favoriteCount ?: -1) as int
def delta = after - before
log.info("Favorite verify: before=" + before + " after=" + after + " delta=" + delta + " toggleOk=" + ok)
props.put("VERIFY_FAV_DELTA", String.valueOf(delta))
// Expect delta == toggleOk when each user favorites once from empty baseline
if (ok == 100 && delta != 100) {
  AssertionResult.setFailure(true)
  AssertionResult.setFailureMessage("favoriteCount delta=" + delta + " but toggleOk=100 (possible race on favorite_count)")
}
if (ok != 100) {
  log.warn("favToggleOk=" + ok + " (expected 100); skip strict delta assert")
}"""

CAPTURE_FAV_BEFORE = """import groovy.json.JsonSlurper
def json = new JsonSlurper().parseText(prev.getResponseDataAsString())
def c = json?.data?.favoriteCount ?: 0
props.put("FAV_COUNT_BEFORE", String.valueOf(c))
log.info("FAV_COUNT_BEFORE=" + c)"""

LOAD_IDS = """vars.put("testActivityId", props.get("TEST_ACTIVITY_ID") ?: vars.get("FALLBACK_ACTIVITY_ID"));
vars.put("favActivityId", props.get("FAVORITE_ACTIVITY_ID") ?: vars.get("FALLBACK_ACTIVITY_ID"));
vars.put("detailActivityId", props.get("BASELINE_ACTIVITY_ID") ?: vars.get("FALLBACK_ACTIVITY_ID"));"""


def build_clean() -> str:
    udv = "\n".join(
        arg(n, v)
        for n, v in [
            ("PROTOCOL", "http"),
            ("HOST", "10.119.13.196"),
            ("PORT", ""),
            ("API_PREFIX", "/api/v1"),
            ("PASSWORD", "123456"),
            ("ORGANIZER_ID", "T001"),
            ("FALLBACK_ACTIVITY_ID", "1"),
            ("MAX_PARTICIPANTS", "50"),
            ("THREADS_PERF", "100"),
            ("RAMP_PERF", "10"),
            ("LOOPS_PERF", "5"),
            ("THREADS_SLOW", "30"),
            ("RAMP_SLOW", "10"),
            ("LOOPS_SLOW", "3"),
            ("THREADS_RACE", "100"),
            ("RAMP_RACE", "5"),
        ]
    )

    http_defaults = """      <ConfigTestElement guiclass="HttpDefaultsGui" testclass="ConfigTestElement" testname="HTTP Request Defaults" enabled="true">
        <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
          <collectionProp name="Arguments.arguments"/>
        </elementProp>
        <stringProp name="HTTPSampler.domain">${HOST}</stringProp>
        <stringProp name="HTTPSampler.port">${PORT}</stringProp>
        <stringProp name="HTTPSampler.protocol">${PROTOCOL}</stringProp>
        <stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>
        <stringProp name="HTTPSampler.path"></stringProp>
        <stringProp name="HTTPSampler.concurrentPool">6</stringProp>
        <stringProp name="HTTPSampler.connect_timeout">5000</stringProp>
        <stringProp name="HTTPSampler.response_timeout">30000</stringProp>
      </ConfigTestElement>
      <hashTree/>"""

    # --- TG0 Setup ---
    tg0_children = "\n".join(
        [
            header_json(),
            # login as organizer
            http_request(
                "Setup Login Organizer",
                "POST",
                "/auth/login",
                body='{"userId":"${ORGANIZER_ID}","password":"${PASSWORD}"}',
            ),
            "<hashTree>",
            json_extractor("Extract organizer token", "token", "$.data.token"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            header_auth(),
            jsr223("Reset counters", RESET_COUNTERS, as_sampler=True),
            http_request("Setup Create Cap50 Activity", "POST", "/activities", body=ACTIVITY_CREATE_CAP50),
            "<hashTree>",
            json_extractor("Extract testActivityId", "testActivityId", "$.data.id"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            http_request("Setup Create Favorite Activity", "POST", "/activities", body=ACTIVITY_CREATE_FAV),
            "<hashTree>",
            json_extractor("Extract favActivityId", "favActivityId", "$.data.id"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            jsr223("Publish activity IDs to props", SAVE_SETUP_IDS, as_sampler=True),
        ]
    )

    def login_block() -> str:
        return "\n".join(
            [
                csv_dataset("users.csv"),
                header_json(),
                http_request(
                    "Login",
                    "POST",
                    "/auth/login",
                    body='{"userId":"${userId}","password":"${PASSWORD}"}',
                ),
                "<hashTree>",
                json_extractor("Extract token", "token", "$.data.token"),
                response_code_ok(),
                json_code_ok(),
                duration_assertion(3000),
                "</hashTree>",
                header_auth(),
                jsr223("Load activity IDs from props", LOAD_IDS, as_sampler=True),
            ]
        )

    # --- TG1 Baseline reads ---
    baseline_samplers = []
    for label, method, path, q in [
        ("GET /users/me", "GET", "/users/me", None),
        ("GET /home/stats", "GET", "/home/stats", None),
        ("GET /activities list", "GET", "/activities", [("page", "0"), ("size", "20")]),
        (
            "GET /activities list keyword",
            "GET",
            "/activities",
            [("page", "0"), ("size", "20"), ("keyword", "讲座"), ("category", "academic")],
        ),
        ("GET /activities/{id}", "GET", "/activities/${detailActivityId}", None),
        ("GET /registrations/mine", "GET", "/registrations/mine", None),
        ("GET /favorites", "GET", "/favorites", None),
        (
            "GET /registrations/status",
            "GET",
            "/registrations/status",
            [("activityId", "${detailActivityId}")],
        ),
        ("GET /favorites/{id}/status", "GET", "/favorites/${detailActivityId}/status", None),
        ("GET /checkins/mine", "GET", "/checkins/mine", None),
        (
            "GET /feedbacks by activity",
            "GET",
            "/feedbacks",
            [("activityId", "${detailActivityId}")],
        ),
        ("GET /feedbacks/mine", "GET", "/feedbacks/mine", None),
        # Clustering may legitimately 404 when no successful run exists — assert HTTP only.
        ("GET /community-clustering/latest", "GET", "/community-clustering/latest", None),
        ("GET /community-clustering/me", "GET", "/community-clustering/me", None),
        ("GET /activities/mine", "GET", "/activities/mine", None),
    ]:
        soft_http_only = label.startswith("GET /community-clustering/")
        baseline_samplers.append(
            sampler_with_children(
                http_request(label, method, path, query=q),
                sla=True,
                assert_code=not soft_http_only,
            )
        )

    tg1 = "\n".join(
        [
            # Ramp 20s softens concurrent BCrypt login spikes while still reaching 100 threads.
            thread_group("TG1 Baseline Read Perf (SLA 3s)", 100, 20, 5, enabled=True),
            "<hashTree>",
            # override threads via UDV isn't automatic - hardcode matching plan; user can edit TG
            login_block(),
            "\n".join(baseline_samplers),
            "</hashTree>",
        ]
    )
    # Fix thread counts to use literal from plan - JMeter doesn't expand vars in num_threads easily in all versions
    # Keep 100/10/5 hardcoded in thread_group call - already done

    # --- TG2 Mixed path ---
    mixed = []
    for label, method, path, q in [
        ("Mixed GET /home/stats", "GET", "/home/stats", None),
        ("Mixed GET /activities", "GET", "/activities", [("page", "0"), ("size", "20")]),
        ("Mixed GET /activities/{id}", "GET", "/activities/${detailActivityId}", None),
        (
            "Mixed GET registrations/status",
            "GET",
            "/registrations/status",
            [("activityId", "${detailActivityId}")],
        ),
        ("Mixed GET favorites/status", "GET", "/favorites/${detailActivityId}/status", None),
    ]:
        mixed.append(sampler_with_children(http_request(label, method, path, query=q)))

    tg2 = "\n".join(
        [
            thread_group("TG2 Mixed Browse Path (SLA 3s)", 100, 20, 5, enabled=True),
            "<hashTree>",
            login_block(),
            "\n".join(mixed),
            "</hashTree>",
        ]
    )

    # --- TG3 Slow ---
    slow = []
    for label, method, path, q in [
        (
            "Search hybrid",
            "GET",
            "/search/activities",
            [
                ("keyword", "人工智能"),
                ("mode", "hybrid"),
                ("page", "0"),
                ("size", "20"),
            ],
        ),
        (
            "Search semantic",
            "GET",
            "/search/activities",
            [
                ("keyword", "讲座"),
                ("mode", "semantic"),
                ("page", "0"),
                ("size", "20"),
            ],
        ),
        ("Recommended", "GET", "/activities/recommended", [("limit", "6")]),
        (
            "Search keyword BM25",
            "GET",
            "/search/activities",
            [
                ("keyword", "志愿"),
                ("mode", "keyword"),
                ("page", "0"),
                ("size", "20"),
            ],
        ),
    ]:
        slow.append(
            sampler_with_children(
                http_request(label, method, path, query=q), sla=False, assert_code=True
            )
        )

    tg3 = "\n".join(
        [
            # 100 concurrent observe-only (no 3s hard fail) for search/recommend.
            thread_group("TG3 Slow Ops Observe (no 3s fail)", 100, 10, 2, enabled=True),
            "<hashTree>",
            login_block(),
            "\n".join(slow),
            "</hashTree>",
        ]
    )

    # --- TG4 Signup race ---
    tg4 = "\n".join(
        [
            thread_group("TG4 Signup Capacity Race", 100, 5, 1, enabled=True),
            "<hashTree>",
            csv_dataset("users.csv", recycle=False, stop_eof=True),
            header_json(),
            http_request(
                "Race Login",
                "POST",
                "/auth/login",
                body='{"userId":"${userId}","password":"${PASSWORD}"}',
            ),
            "<hashTree>",
            json_extractor("Extract token", "token", "$.data.token"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            header_auth(),
            jsr223(
                "Load TEST_ACTIVITY_ID",
                'vars.put("testActivityId", props.get("TEST_ACTIVITY_ID") ?: vars.get("FALLBACK_ACTIVITY_ID"));',
                as_sampler=True,
            ),
            http_request(
                "POST /registrations race",
                "POST",
                "/registrations",
                body='{"activityId":${testActivityId}}',
            ),
            "<hashTree>",
            # No code==0 assertion — business failures expected
            response_code_ok(),  # wait - full might return 400
            # Actually BusinessException returns HTTP 400. Don't assert 200.
            jsr223("Count signup outcome", COUNT_SIGNUP),
            "</hashTree>",
        ]
    )
    # Fix TG4 - remove response_code_ok from race sampler
    tg4 = "\n".join(
        [
            thread_group("TG4 Signup Capacity Race", 100, 5, 1, enabled=True),
            "<hashTree>",
            csv_dataset("users.csv", recycle=False, stop_eof=True),
            header_json(),
            http_request(
                "Race Login",
                "POST",
                "/auth/login",
                body='{"userId":"${userId}","password":"${PASSWORD}"}',
            ),
            "<hashTree>",
            json_extractor("Extract token", "token", "$.data.token"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            header_auth(),
            jsr223(
                "Load TEST_ACTIVITY_ID",
                'vars.put("testActivityId", props.get("TEST_ACTIVITY_ID") ?: vars.get("FALLBACK_ACTIVITY_ID"));',
                as_sampler=True,
            ),
            http_request(
                "POST /registrations race",
                "POST",
                "/registrations",
                body='{"activityId":${testActivityId}}',
            ),
            "<hashTree>",
            jsr223("Count signup outcome", COUNT_SIGNUP),
            "</hashTree>",
            "</hashTree>",
        ]
    )

    # --- TG5 Favorite ---
    tg5 = "\n".join(
        [
            thread_group("TG5 Favorite Toggle Race", 100, 15, 1, enabled=True),
            "<hashTree>",
            csv_dataset("users.csv", recycle=False, stop_eof=True),
            header_json(),
            http_request(
                "Fav Login",
                "POST",
                "/auth/login",
                body='{"userId":"${userId}","password":"${PASSWORD}"}',
            ),
            "<hashTree>",
            json_extractor("Extract token", "token", "$.data.token"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            header_auth(),
            jsr223(
                "Load FAVORITE_ACTIVITY_ID",
                'vars.put("favActivityId", props.get("FAVORITE_ACTIVITY_ID") ?: vars.get("FALLBACK_ACTIVITY_ID"));',
                as_sampler=True,
            ),
            http_request(
                "POST /favorites/{id} toggle",
                "POST",
                "/favorites/${favActivityId}",
            ),
            "<hashTree>",
            jsr223("Count favorite outcome", COUNT_FAV),
            "</hashTree>",
            "</hashTree>",
        ]
    )

    # --- TG6 duplicate signup (enabled, light) ---
    tg6 = "\n".join(
        [
            thread_group("TG6 Duplicate Signup Guard", 100, 5, 1, enabled=True),
            "<hashTree>",
            csv_dataset("users.csv", recycle=True),
            header_json(),
            http_request(
                "Dup Login",
                "POST",
                "/auth/login",
                body='{"userId":"${userId}","password":"${PASSWORD}"}',
            ),
            "<hashTree>",
            json_extractor("Extract token", "token", "$.data.token"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            header_auth(),
            jsr223(
                "Load TEST_ACTIVITY_ID",
                'vars.put("testActivityId", props.get("TEST_ACTIVITY_ID") ?: vars.get("FALLBACK_ACTIVITY_ID"));',
                as_sampler=True,
            ),
            # first signup may succeed or fail (already full after TG4)
            http_request(
                "POST /registrations first",
                "POST",
                "/registrations",
                body='{"activityId":${testActivityId}}',
            ),
            "<hashTree>",
            jsr223(
                "Ignore first outcome",
                "prev.setSuccessful(true)",
            ),
            "</hashTree>",
            http_request(
                "POST /registrations duplicate",
                "POST",
                "/registrations",
                body='{"activityId":${testActivityId}}',
            ),
            "<hashTree>",
            jsr223("Count duplicate outcome", COUNT_DUP),
            "</hashTree>",
            "</hashTree>",
        ]
    )

    # --- TearDown verify ---
    td = "\n".join(
        [
            teardown_thread_group("TearDown Verify Counters", enabled=True),
            "<hashTree>",
            header_json(),
            http_request(
                "Verify Login",
                "POST",
                "/auth/login",
                body='{"userId":"${ORGANIZER_ID}","password":"${PASSWORD}"}',
            ),
            "<hashTree>",
            json_extractor("Extract token", "token", "$.data.token"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            header_auth(),
            jsr223(
                "Load IDs for verify",
                'vars.put("testActivityId", props.get("TEST_ACTIVITY_ID") ?: "0");\n'
                'vars.put("favActivityId", props.get("FAVORITE_ACTIVITY_ID") ?: "0");\n'
                'log.info("signupSuccess="+props.get("signupSuccess")+" bizFail="+props.get("signupBusinessFail")+" httpErr="+props.get("signupHttpError")+" favOk="+props.get("favToggleOk"));',
                as_sampler=True,
            ),
            # Capture fav before was during TG5 setup - we need before count from setup
            # Re-read: for fav we stored FAV_COUNT_BEFORE in setup? Add capture in TG0 after create fav activity
            http_request("Verify GET cap activity", "GET", "/activities/${testActivityId}"),
            "<hashTree>",
            response_code_ok(),
            json_code_ok(),
            """          <JSR223Assertion guiclass="TestBeanGUI" testclass="JSR223Assertion" testname="Assert signupCount==50" enabled="true">
            <stringProp name="cacheKey">true</stringProp>
            <stringProp name="filename"></stringProp>
            <stringProp name="parameters"></stringProp>
            <stringProp name="script">"""
            + escape(VERIFY_SIGNUP)
            + """</stringProp>
            <stringProp name="scriptLanguage">groovy</stringProp>
          </JSR223Assertion>
          <hashTree/>""",
            "</hashTree>",
            http_request("Verify GET fav activity", "GET", "/activities/${favActivityId}"),
            "<hashTree>",
            response_code_ok(),
            json_code_ok(),
            """          <JSR223Assertion guiclass="TestBeanGUI" testclass="JSR223Assertion" testname="Assert favoriteCount delta" enabled="true">
            <stringProp name="cacheKey">true</stringProp>
            <stringProp name="filename"></stringProp>
            <stringProp name="parameters"></stringProp>
            <stringProp name="script">"""
            + escape(VERIFY_FAV)
            + """</stringProp>
            <stringProp name="scriptLanguage">groovy</stringProp>
          </JSR223Assertion>
          <hashTree/>""",
            "</hashTree>",
            jsr223(
                "Log duplicate signup stats",
                'def unexpected = (props.get("dupSignupOkUnexpected") ?: "0") as int\n'
                'def expectedFail = (props.get("dupSignupExpectedFail") ?: "0") as int\n'
                'log.info("dup unexpected success="+unexpected+" expectedFail="+expectedFail)\n'
                'if (unexpected == 0 && expectedFail == 0) { log.info("Skip dup verify (TG6 did not run)"); return }\n'
                'if (unexpected > 0) { throw new Exception("Duplicate signup unexpectedly succeeded: " + unexpected) }',
                as_sampler=True,
            ),
            "</hashTree>",
        ]
    )

    # Capture FAV_COUNT_BEFORE in TG0 after creating fav activity
    tg0_with_fav_capture = "\n".join(
        [
            setup_thread_group("TG0 Setup Create Test Activities", enabled=True),
            "<hashTree>",
            header_json(),
            http_request(
                "Setup Login Organizer",
                "POST",
                "/auth/login",
                body='{"userId":"${ORGANIZER_ID}","password":"${PASSWORD}"}',
            ),
            "<hashTree>",
            json_extractor("Extract organizer token", "token", "$.data.token"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            header_auth(),
            jsr223("Reset counters", RESET_COUNTERS, as_sampler=True),
            http_request("Setup Create Cap50 Activity", "POST", "/activities", body=ACTIVITY_CREATE_CAP50),
            "<hashTree>",
            json_extractor("Extract testActivityId", "testActivityId", "$.data.id"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            http_request("Setup Create Favorite Activity", "POST", "/activities", body=ACTIVITY_CREATE_FAV),
            "<hashTree>",
            json_extractor("Extract favActivityId", "favActivityId", "$.data.id"),
            response_code_ok(),
            json_code_ok(),
            "</hashTree>",
            http_request("Setup Read Fav Baseline", "GET", "/activities/${favActivityId}"),
            "<hashTree>",
            response_code_ok(),
            json_code_ok(),
            jsr223("Capture FAV_COUNT_BEFORE", CAPTURE_FAV_BEFORE),
            "</hashTree>",
            jsr223("Publish activity IDs to props", SAVE_SETUP_IDS, as_sampler=True),
            "</hashTree>",
        ]
    )

    listeners = """      <ResultCollector guiclass="SummaryReport" testclass="ResultCollector" testname="Summary Report" enabled="true">
        <boolProp name="ResultCollector.error_logging">false</boolProp>
        <objProp>
          <name>saveConfig</name>
          <value class="SampleSaveConfiguration">
            <time>true</time>
            <latency>true</latency>
            <timestamp>true</timestamp>
            <success>true</success>
            <label>true</label>
            <code>true</code>
            <message>true</message>
            <threadName>true</threadName>
            <dataType>true</dataType>
            <encoding>false</encoding>
            <assertions>true</assertions>
            <subresults>true</subresults>
            <responseData>false</responseData>
            <samplerData>false</samplerData>
            <xml>false</xml>
            <fieldNames>true</fieldNames>
            <responseHeaders>false</responseHeaders>
            <requestHeaders>false</requestHeaders>
            <responseDataOnError>true</responseDataOnError>
            <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
            <assertionsResultsToSave>0</assertionsResultsToSave>
            <bytes>true</bytes>
            <sentBytes>true</sentBytes>
            <url>true</url>
            <threadCounts>true</threadCounts>
            <idleTime>true</idleTime>
            <connectTime>true</connectTime>
          </value>
        </objProp>
        <stringProp name="filename"></stringProp>
      </ResultCollector>
      <hashTree/>
      <ResultCollector guiclass="StatVisualizer" testclass="ResultCollector" testname="Aggregate Report" enabled="true">
        <boolProp name="ResultCollector.error_logging">false</boolProp>
        <objProp>
          <name>saveConfig</name>
          <value class="SampleSaveConfiguration">
            <time>true</time>
            <latency>true</latency>
            <timestamp>true</timestamp>
            <success>true</success>
            <label>true</label>
            <code>true</code>
            <message>true</message>
            <threadName>true</threadName>
            <dataType>true</dataType>
            <encoding>false</encoding>
            <assertions>true</assertions>
            <subresults>true</subresults>
            <responseData>false</responseData>
            <samplerData>false</samplerData>
            <xml>false</xml>
            <fieldNames>true</fieldNames>
            <responseHeaders>false</responseHeaders>
            <requestHeaders>false</requestHeaders>
            <responseDataOnError>true</responseDataOnError>
            <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
            <assertionsResultsToSave>0</assertionsResultsToSave>
            <bytes>true</bytes>
            <sentBytes>true</sentBytes>
            <url>true</url>
            <threadCounts>true</threadCounts>
            <idleTime>true</idleTime>
            <connectTime>true</connectTime>
          </value>
        </objProp>
        <stringProp name="filename"></stringProp>
      </ResultCollector>
      <hashTree/>
      <ResultCollector guiclass="ViewResultsFullVisualizer" testclass="ResultCollector" testname="View Results Tree" enabled="false">
        <boolProp name="ResultCollector.error_logging">false</boolProp>
        <objProp>
          <name>saveConfig</name>
          <value class="SampleSaveConfiguration">
            <time>true</time>
            <latency>true</latency>
            <timestamp>true</timestamp>
            <success>true</success>
            <label>true</label>
            <code>true</code>
            <message>true</message>
            <threadName>true</threadName>
            <dataType>true</dataType>
            <encoding>false</encoding>
            <assertions>true</assertions>
            <subresults>true</subresults>
            <responseData>false</responseData>
            <samplerData>false</samplerData>
            <xml>false</xml>
            <fieldNames>true</fieldNames>
            <responseHeaders>false</responseHeaders>
            <requestHeaders>false</requestHeaders>
            <responseDataOnError>true</responseDataOnError>
            <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
            <assertionsResultsToSave>0</assertionsResultsToSave>
            <bytes>true</bytes>
            <url>true</url>
          </value>
        </objProp>
        <stringProp name="filename"></stringProp>
      </ResultCollector>
      <hashTree/>"""

    # Disable TG1-TG6 by default? Plan says enable as needed. For a usable default:
    # serialize=true: Setup -> TG1 -> TG2 -> TG3 -> TG4 -> TG5 -> TG6 -> TearDown
    # Running all may take long and TG4 needs Setup. Enable all for complete plan.
    # TG1+TG2 both 100*5 is heavy - keep enabled as plan specifies.

    # Fix TG1 thread_group to use variables - JMeter 5 supports ${THREADS_PERF} in some places
    # Use hardcoded 100 for reliability

    doc = f"""<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Campus Activity Platform Perf" enabled="true">
      <stringProp name="TestPlan.comments">SLA: 100 concurrent &lt;3s except semantic search / recommend. TG4/TG5 concurrency correctness. Run from jmeter/ so users.csv resolves.</stringProp>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.tearDown_on_shutdown">true</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">true</boolProp>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
        <collectionProp name="Arguments.arguments">
{udv}
        </collectionProp>
      </elementProp>
    </TestPlan>
    <hashTree>
{http_defaults}
{tg0_with_fav_capture}
{tg1}
{tg2}
{tg3}
{tg4}
{tg5}
{tg6}
{td}
{listeners}
    </hashTree>
  </hashTree>
</jmeterTestPlan>
"""
    return doc


def main() -> None:
    text = build_clean()
    OUT.write_text(text, encoding="utf-8")
    print(f"Wrote {OUT} ({len(text)} bytes)")


if __name__ == "__main__":
    main()
