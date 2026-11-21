"""Aceptación sobre Auth y Forum locales con PostgreSQL. Usa cuentas nuevas en cada ejecución."""
import json
import os
import secrets
from urllib.error import HTTPError
from urllib.request import Request, build_opener, ProxyHandler

http = build_opener(ProxyHandler({}))
AUTH = "http://127.0.0.1:8081/api/v1/auth"
FORUM = "http://127.0.0.1:8082/api/v1"
checks = 0


def call(method, url, status, data=None, token=None):
    global checks
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = Request(url, method=method, headers=headers,
                  data=json.dumps(data).encode() if data is not None else None)
    try:
        res = http.open(req, timeout=15)
    except HTTPError as err:
        res = err
    with res:
        assert res.status == status, (method, url, res.status, status)
        checks += 1
        raw = res.read()
    return json.loads(raw) if raw else None


def account():
    email = secrets.token_hex(10) + "@example.test"
    password = secrets.token_hex(16)
    data = dict(email=email, password=password, fullName="Cuenta de prueba")
    uid = call("POST", AUTH + "/register", 201, data)["userId"]
    call("POST", AUTH + "/register", 409, {**data, "email": email.upper()})
    call("POST", AUTH + "/login", 401, dict(email=email, password="incorrecta"))
    token = call("POST", AUTH + "/login", 200, dict(email=email, password=password))["accessToken"]
    assert call("GET", AUTH + "/me", 200, token=token)["id"] == uid
    return token


def main():
    author, peer = account(), account()
    profile = dict(fullName="Perfil actualizado", academicProgram="Sistemas", biography="Tutoría entre pares")
    call("PUT", AUTH + "/me", 200, profile, author)
    assert call("GET", AUTH + "/me", 200, token=author)["academicProgram"] == "Sistemas"
    assert not call("GET", AUTH + "/me", 200, token=peer)["academicProgram"]
    call("PUT", AUTH + "/me", 400, {**profile, "fullName": " "}, author)
    call("GET", AUTH + "/me", 401)
    print("HU01–04 · registro, acceso, consulta y persistencia del perfil")

    posts = FORUM + "/posts"
    post = dict(title="Matrices", content="¿Cómo se diagonaliza una matriz?", category="Matemática")
    call("POST", posts, 401, post)
    call("POST", posts, 400, {**post, "title": ""}, author)
    pid = call("POST", posts, 201, post, author)["postId"]
    thread = f"{posts}/{pid}"
    comments = thread + "/comments"
    call("PUT", thread, 403, post, peer)
    call("PUT", thread, 204, {**post, "title": "Título corregido"}, author)
    feed = call("GET", posts, 200)
    assert next(p for p in feed if p["id"] == pid)["title"] == "Título corregido"
    replies = []
    for text in ["Primera respuesta", "Segunda respuesta"]:
        replies.append(call("POST", comments, 201, {"content": text}, peer)["commentId"])
    assert [c["id"] for c in call("GET", comments, 200)] == replies
    first = comments + "/" + str(replies[0])
    call("PUT", first, 403, {"content": "Edición ajena"}, author)
    call("PUT", first, 204, {"content": "Respuesta corregida"}, peer)
    call("DELETE", thread, 403, token=peer)
    call("DELETE", thread, 204, token=author)
    marker = next(p for p in call("GET", posts, 200) if p["id"] == pid)
    assert marker["title"] == "Consulta retirada" and not marker["content"] and marker["authorId"] is None
    assert call("GET", comments, 200)[0]["content"] == "Respuesta corregida"
    call("POST", comments, 409, {"content": "Nuevo comentario"}, peer)
    call("DELETE", first, 403, token=author)
    call("DELETE", first, 204, token=peer)
    assert [c["id"] for c in call("GET", comments, 200)] == replies[1:]
    call("DELETE", comments + "/" + str(replies[1]), 204, token=peer)
    assert all(p["id"] != pid for p in call("GET", posts, 200))
    print("HU06–13 · lectura pública, autoría, orden, edición y retiro con conservación del hilo")

    if not os.environ.get("FORO_TEST_MOD_EMAIL"):
        print("Moderación omitida: falta una cuenta de pruebas con rol MODERATOR.")
    else:
        mod = call("POST", AUTH + "/login", 200, dict(email=os.environ["FORO_TEST_MOD_EMAIL"],
                   password=os.environ["FORO_TEST_MOD_PASSWORD"]))["accessToken"]
        pid = call("POST", posts, 201, post, author)["postId"]
        comments = f"{posts}/{pid}/comments"
        cid = call("POST", comments, 201, dict(content="Texto bajo revisión"), peer)["commentId"]
        reports = FORUM + "/reports"
        data = dict(postId=pid, commentId=cid, reason="Solicito revisión")
        call("POST", reports, 400, {**data, "reason": ""}, peer)
        rid = call("POST", reports, 201, data, author)["id"]
        call("GET", reports, 403, token=peer)
        assert any(r["id"] == rid and r["content"] == "Texto bajo revisión" for r in call("GET", reports, 200, token=mod))
        decision = dict(decision="DISMISSED", reason="Cumple las normas.")
        call("PUT", f"{reports}/{rid}", 403, decision, peer)
        call("PUT", f"{reports}/{rid}", 400, {**decision, "reason": ""}, mod)
        call("PUT", f"{reports}/{rid}", 204, decision, mod)
        call("PUT", f"{reports}/{rid}", 409, decision, mod)
        assert call("GET", comments, 200)[0]["id"] == cid
        for target in [cid, None]:
            rid = call("POST", reports, 201, {**data, "commentId": target}, author)["id"]
            call("PUT", f"{reports}/{rid}", 204, dict(decision="REMOVED", reason="Incumple la norma del foro"), mod)
            if target:
                assert not call("GET", comments, 200)
        assert all(p["id"] != pid for p in call("GET", posts, 200))
        call("GET", comments, 404)
        print("HU21–23 y HU36 · reporte, bandeja restringida, retiro y desestimación")

    if os.getenv("FORO_BOOKS_FIXTURE") == "1":
        books = FORUM + "/books?q="
        book = call("GET", books + "algebra", 200)[0]
        assert not call("GET", books + "sinresultados", 200)
        call("GET", books + "caida", 503)
        pid = call("POST", posts, 201, post, author)["postId"]
        comments = f"{posts}/{pid}/comments"
        call("POST", comments, 201, dict(content="Referencia del tema", bookId=book["id"]), peer)
        citation = call("GET", comments, 200)[0]
        assert citation["bookId"] == book["id"] and citation["bookTitle"] == book["title"]
        print("HU27 y HU37 · resultados, vacío, fallo temporal y referencia persistente con proveedor de prueba")
    print(f"{checks} respuestas HTTP verificadas")


if __name__ == "__main__":
    main()
