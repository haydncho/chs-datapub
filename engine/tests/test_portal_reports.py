from fastapi.testclient import TestClient

from dpub_engine.main import app

client = TestClient(app)
URL = "/v1/portal-reports/quiz/grade"


def test_grade_correct_answer():
    r = client.post(URL, json={"items": [{"quizId": 1, "optionCount": 4, "picked": 1, "answer": 1}]})
    assert r.status_code == 200
    body = r.json()
    assert body["items"][0] == {"quizId": 1, "picked": 1, "correct": True, "correctIndex": 1, "result": "回答正确"}
    assert body["score"] == 1 and body["total"] == 1 and body["passed"] is True and body["accuracyPct"] == 100.0


def test_grade_wrong_answer_reveals_correct_option_letter():
    body = client.post(URL, json={"items": [{"quizId": 1, "optionCount": 4, "picked": 0, "answer": 1}]}).json()
    item = body["items"][0]
    assert item["correct"] is False
    assert item["correctIndex"] == 1
    assert item["result"] == "回答错误,正确答案为 B"
    assert body["passed"] is False


def test_grade_summary_over_several_items():
    items = [
        {"quizId": 1, "optionCount": 4, "picked": 1, "answer": 1},
        {"quizId": 2, "optionCount": 4, "picked": 2, "answer": 2},
        {"quizId": 3, "optionCount": 3, "picked": 0, "answer": 2},
    ]
    body = client.post(URL, json={"items": items}).json()
    assert body["score"] == 2 and body["total"] == 3
    assert body["accuracyPct"] == 66.7
    assert body["passed"] is True


def test_grade_rejects_out_of_range_option():
    r = client.post(URL, json={"items": [{"quizId": 1, "optionCount": 4, "picked": 4, "answer": 1}]})
    assert r.status_code == 422
    assert "请求参数不合法" in r.json()["detail"]


def test_grade_rejects_empty():
    assert client.post(URL, json={"items": []}).status_code == 422
