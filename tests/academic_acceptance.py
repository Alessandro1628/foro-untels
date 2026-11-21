"""Aceptación sobre bases desechables. Usa el administrador de pruebas y proveedores locales."""
import json
import os
import secrets
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.request import Request
from sprint_acceptance import AUTH, FORUM, call, http

BASE = FORUM + "/academic"
FIXTURE = "http://127.0.0.1:18091"
suffix = secrets.token_hex(4)


def member(label):
    email = label.lower() + suffix + "@example.test"
    password = secrets.token_hex(16)
    uid = call("POST", AUTH + "/register", 201, dict(email=email, password=password, fullName=label))["userId"]
    token = login(email, password)
    return uid, email, token


def login(email, password):
    return call("POST", AUTH + "/login", 200, dict(email=email, password=password))["accessToken"]


def raw(method, url, token=None, data=None, headers=None):
    hdr = dict(headers or {})
    if token:
        hdr["Authorization"] = "Bearer " + token
    try:
        response = http.open(Request(url, method=method, headers=hdr, data=data), timeout=10)
    except HTTPError as error:
        response = error
    with response:
        return response.status, response.read()


def submit_material(token, fields, pdf=None, expected=200):
    boundary = "form-" + secrets.token_hex(10)
    parts = []
    for key, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
    if pdf is not None:
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="prueba.pdf"\r\nContent-Type: application/pdf\r\n\r\n'.encode() + pdf + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    status, body = raw("POST", BASE + "/materials", token, b"".join(parts),
                       {"Content-Type": "multipart/form-data; boundary=" + boundary})
    assert status == expected, (status, body[:280])
    return json.loads(body) if status == 200 else None


def until(fn):
    for _ in range(60):
        value = fn()
        if value:
            return value
        time.sleep(.25)
    raise AssertionError("No se alcanzó el estado esperado")


def catalog(admin, author, kind):
    base = BASE + "/catalogs/" + kind
    call("POST", base, 403, {"name": "Inválido"}, author)
    return call("POST", base, 201, {"name": kind + suffix}, admin)


def forum_tests(admin, author, peer, peer_email):
    admin_roles = AUTH + "/moderators"
    call("PUT", admin_roles, 204, dict(email=peer_email, granted=True), admin)
    assert call("GET", AUTH + "/me", 200, token=peer)["role"] == "MODERATOR"
    proposals = BASE + "/proposals"
    proposal = {"name": "Comunidad " + suffix, "purpose": "Estudiar juntos"}
    pid = call("POST", proposals, 201, proposal, author)
    call("GET", proposals, 403, token=author)
    assert any(p["id"] == pid for p in call("GET", proposals, 200, token=peer))
    call("PUT", proposals + "/" + str(pid), 204, {"approve": True}, peer)
    call("PUT", admin_roles, 204, dict(email=peer_email, granted=False), admin)
    call("GET", proposals, 403, token=peer)
    directory = BASE + "/communities"
    comm = next(c for c in call("GET", directory, 200) if c["name"] == proposal["name"])
    direct = call("POST", directory, 201, {**proposal, "name": "Otra " + suffix}, admin)
    follow = directory + "/" + str(comm["id"]) + "/follow"
    call("PUT", follow, 204, token=author)
    cat = catalog(admin, author, "categories")
    post = dict(title="Primera publicación", content="Una consulta de prueba.", category="categories" + suffix, communityId=comm["id"])
    posts = FORUM + "/posts"
    p = call("POST", posts, 201, post, author)["postId"]
    other = call("POST", posts, 201, {**post, "title": "Otra", "communityId":direct}, peer)["postId"]
    comments = posts + "/" + str(p) + "/comments"
    parent = call("POST", comments, 201, {"content":"Comentario padre"}, peer)["commentId"]
    call("POST", comments, 201, {"content":"Respuesta", "parentId":parent}, author)
    assert call("GET", comments, 200)[1]["parentId"] == parent
    call("POST", f"{posts}/{other}/comments", 404, {"content":"Cruce", "parentId":parent}, peer)
    feed = BASE + "/feed"
    for _ in range(2):
        call("PUT", f"{feed}/{p}/vote", 204, token=peer)
        call("PUT", f"{feed}/{p}/favorite", 204, token=author)
    def ids(query, token=author):
        return [r["id"] for r in call("GET", feed + query, 200, token=token)]
    assert ids("?scope=FAVORITES") == [p] and not ids("?scope=FAVORITES", peer)
    assert ids("?scope=FOLLOWING&sort=FEATURED") == [p]
    assert ids("?community=" + str(direct)) == [other]
    assert ids("?sort=RECENT").index(other) < ids("?sort=RECENT").index(p)
    assert ids("?sort=FEATURED").index(p) < ids("?sort=FEATURED").index(other)
    call("PUT", f"{feed}/{other}/vote",204,token=peer)
    assert ids("?sort=FEATURED").index(other) < ids("?sort=FEATURED").index(p)
    call("PUT", f"{directory}/{direct}/follow",204,token=author)
    assert ids("?scope=FOLLOWING&sort=FEATURED") == [other,p]
    call("DELETE", f"{directory}/{direct}/follow",204,token=author)
    assert next(r for r in call("GET",feed,200) if r["id"] == p)["votes"] == 1
    call("DELETE", f"{feed}/{p}/favorite", 204, token=author)
    call("DELETE", follow, 204, token=author)
    assert not ids("?scope=FAVORITES") and not ids("?scope=FOLLOWING")
    category = BASE + "/catalogs/categories/" + str(cat)
    call("PUT", category, 204, {"name": "Nueva " + suffix}, admin)
    assert next(r for r in call("GET",feed,200) if r["id"] == p)["category"] == "Nueva " + suffix
    call("DELETE", category, 204, token=admin)
    call("POST", posts, 400, {**post, "category": "Nueva " + suffix}, author)
    print("Foro, respuestas, categorías, seguimiento, favoritos, votos y permisos verificados.")


def library_tests(admin, tutor, student, subject):
    fields = dict(title="Álgebra " + suffix, author="Autor bibliográfico", subject=subject)
    pdf = b"%PDF-1.4\n% documento de prueba\n%%EOF"
    resources = BASE + "/materials"
    upload = submit_material(tutor, fields, pdf)
    file_url = f"{resources}/{upload}/pdf"
    assert raw("GET", file_url)[0] == 404
    assert raw("GET", file_url, admin) == (200, pdf)
    assert raw("GET", file_url, student)[0] == 404
    assert not call("GET", resources + "?q=" + suffix, 200)
    assert call("GET", FORUM + "/books?q=vista", 200)[0]["previewUrl"]
    assert call("GET", FORUM + "/books?q=algebra", 200)[0]["previewUrl"] is None
    review = f"{resources}/{upload}/review"
    call("PUT", review, 204, dict(approve=False, version=1, reason="Revisar ficha"), admin)
    submit_material(student, {**fields, "revision":upload}, pdf, 409)
    assert submit_material(tutor, {**fields, "revision":upload}, pdf) == upload
    call("PUT", review, 409, dict(approve=True, version=1), admin)
    call("PUT", review, 204, dict(approve=True, version=2), admin)
    assert raw("GET", file_url) == (200, pdf)
    link = submit_material(student, {**fields, "url":"https://example.org/libro"})
    call("PUT", f"{resources}/{link}/review", 204, dict(approve=True, version=1), admin)
    catalog_rows = call("GET", resources + f"?subject={subject}&q={suffix}", 200)
    assert len(catalog_rows) == 2
    item = next(m for m in catalog_rows if m["id"] == upload)
    assert item["teacher"] and item["author"] == fields["author"] and item["shared_by"] != item["author"]
    past = call("GET", f"{resources}/{upload}/history", 200, token=tutor)
    assert [r["decision"] for r in past] == ["OBSERVED","APPROVED"]
    call("GET", f"{resources}/{upload}/history", 404, token=student)
    assert "observation" not in item and item["version"] == 2
    assert past[0]["reason"] == "Revisar ficha"
    assert next(m for m in catalog_rows if m["id"] == link)["url"] == "https://example.org/libro"
    assert not call("GET", resources + "?subject=0", 200)
    submit_material(tutor, fields, b"plain text", 400)
    print("Biblioteca: PDF privado y publicado, búsqueda, enlace, revisión, versión y procedencia verificados.")


def tutoring_tests(admin, tutor, student, peer, subject, tutor_email, student_email, peer_email):
    creds = BASE + "/credentials"
    for kind in ("TUTOR", "TEACHER"):
        data = dict(kind=kind, evidence="Sustento académico", subject=subject if kind == "TUTOR" else None)
        cid = call("POST", creds, 201, data, tutor)
        call("PUT", creds + "/" + str(cid), 403, {"approve":True}, student)
        call("PUT", creds + "/" + str(cid), 204, {"approve":True}, admin)
        if kind == "TUTOR":
            credential = cid
    assert any(t["id"] == credential for t in call("GET", BASE + "/tutors?subject=" + str(subject), 200))
    denied = call("POST", creds, 201, dict(kind="TUTOR",subject=subject,evidence="Prueba de rechazo"), peer)
    call("PUT", creds + "/" + str(denied), 204, {"approve":False}, admin)
    assert all(t["id"] != denied for t in call("GET", BASE + "/tutors", 200))
    start = datetime.now(timezone.utc) + timedelta(days=2)
    data = dict(credential=credential, start=start.isoformat(), minutes=45)
    slot = call("POST", BASE + "/slots", 201, data, tutor)
    call("POST", BASE + "/slots", 409, data, tutor)
    assert any(s["id"] == slot for s in call("GET", BASE + "/slots?credential=" + str(credential), 200))
    reserve = BASE + "/slots/" + str(slot) + "/reserve"
    with ThreadPoolExecutor(2) as pool:
        results = list(pool.map(lambda t: raw("PUT", reserve, t)[0], [student, peer]))
    assert sorted(results) == [204,409], results
    winner, email = (student, student_email) if results[0]==204 else (peer,peer_email)
    def session(token):
        return next(s for s in call("GET",BASE + "/sessions",200,token=token) if s["id"]==slot)
    until(lambda: session(winner)["sync_state"] == "SYNCED")
    assert session(tutor)["meet_url"] == session(winner)["meet_url"]
    events = call("GET", FIXTURE + "/events", 200)
    matches = [e for e in events if {a["email"] for a in e["attendees"]} == {tutor_email,email}]
    assert len(matches)==1 and datetime.fromisoformat(matches[0]["start"]["dateTime"]) == start
    call("PUT", f"{BASE}/sessions/{slot}/rating", 409, {"stars":4}, winner)
    print("Acreditaciones, disponibilidad, reserva simultánea, agenda privada e invitación con Meet verificadas.")
    return slot, winner


def recovery(token, email):
    call("POST", AUTH + "/password/request", 204, {"email":email})
    message = until(lambda: call("GET", FIXTURE + "/mail", 200).get(email))
    key = message.split("#reset=")[1]
    data = dict(token=key, password=secrets.token_hex(12))
    call("POST", AUTH + "/password/reset", 204, data)
    call("POST", AUTH + "/password/reset", 400, data)
    call("GET", AUTH + "/me", 401, token=token)
    login(email,data["password"])
    print("Recuperación por enlace, uso único e invalidación de la sesión anterior verificadas.")


if __name__ == "__main__":
    admin = login(os.environ["FORO_TEST_ADMIN_EMAIL"], os.environ["FORO_TEST_ADMIN_PASSWORD"])
    _, tutor_email, tutor = member("Tutor")
    _, student_email, student = member("Estudiante")
    _, peer_email, peer = member("Otro")
    subject = catalog(admin, tutor, "subjects")
    forum_tests(admin,tutor,peer,peer_email)
    tutoring_tests(admin,tutor,student,peer,subject,tutor_email,student_email,peer_email)
    library_tests(admin,tutor,student,subject)
    subject_url = BASE + "/catalogs/subjects/" + str(subject)
    call("PUT",subject_url,204,{"name":"Asignatura " + suffix},admin)
    call("DELETE",subject_url,204,token=admin)
    assert call("GET",BASE + "/materials?subject=" + str(subject),200)[0]["subject"] == "Asignatura " + suffix
    submit_material(tutor,dict(title="Nuevo",author="Autor",subject=subject,url="https://example.org"),expected=400)
    recovery(student,student_email)
