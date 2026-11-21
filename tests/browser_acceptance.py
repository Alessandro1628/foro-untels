"""Recorridos en Chromium sobre la aplicación local y el proveedor Books de prueba."""
import os
import secrets
from playwright.sync_api import expect, sync_playwright

BASE = "http://127.0.0.1:5173"


def login(page, email, password):
    page.get_by_role("button", name="Iniciar sesión", exact=True).click()
    dialog = page.get_by_role("dialog")
    dialog.get_by_label("Correo electrónico").fill(email)
    dialog.get_by_label("Contraseña", exact=True).fill(password)
    dialog.get_by_role("button", name="Iniciar sesión").click()
    expect(page.get_by_role("button", name="Mi perfil")).to_be_visible()


def register(page):
    email = secrets.token_hex(8) + "@example.test"
    password = secrets.token_hex(12)
    page.get_by_role("button", name="Registrarse").click()
    dialog = page.get_by_role("dialog")
    dialog.get_by_label("Nombre completo").fill("Estudiante de prueba")
    dialog.get_by_label("Correo electrónico").fill(email)
    dialog.get_by_label("Contraseña", exact=True).fill(password)
    dialog.get_by_role("button", name="Crear cuenta").click()
    expect(dialog.get_by_text("Cuenta registrada correctamente.", exact=False)).to_be_visible()
    dialog.get_by_label("Contraseña", exact=True).fill(password)
    dialog.get_by_role("button", name="Iniciar sesión").click()
    expect(page.get_by_role("button", name="Mi perfil")).to_be_visible()


def article(page, title):
    return page.locator("article").filter(has=page.get_by_role("heading", name=title, exact=True))


def open_replies(card):
    card.get_by_role("button", name="Ver respuestas y comentar", exact=False).click()


def check_width(page, width):
    page.set_viewport_size({"width": width, "height": 900})
    assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), width


def run(browser):
    errors = []
    author = browser.new_page(viewport={"width": 768, "height": 900})
    author.on("pageerror", lambda error: errors.append(str(error)))
    author.goto(BASE)
    for width in [360, 767, 768, 769, 1280]:
        check_width(author, width)
        expect(author.get_by_role("button", name="Registrarse")).to_be_visible()
    check_width(author, 767)
    register(author)
    author.get_by_role("button", name="Mi perfil").click()
    author.get_by_label("Carrera o especialidad").fill("Ingeniería de Sistemas")
    author.get_by_role("button", name="Guardar perfil").click()
    expect(author.get_by_text("Perfil actualizado.", exact=True)).to_be_visible()
    author.reload()
    author.get_by_role("button", name="Mi perfil").click()
    expect(author.get_by_label("Carrera o especialidad")).to_have_value("Ingeniería de Sistemas")
    author.get_by_role("button", name="Volver al foro").click()

    title = "Sistemas " + secrets.token_hex(5)
    author.get_by_role("button", name="+ Nueva pregunta").click()
    author.get_by_label("Título de la pregunta").fill(title)
    author.get_by_label("Categoría académica").select_option("Programación")
    author.get_by_label("Comunidad", exact=True).select_option(label="Ingeniería de Sistemas")
    author.get_by_label("Describe tu pregunta").fill("Consulta sobre estructuras y listas.")
    author.get_by_role("button", name="Publicar pregunta").click()
    expect(author.get_by_text("Pregunta publicada correctamente.", exact=False)).to_be_visible()
    author.reload()
    expect(article(author, title).get_by_text("Comunidad: Ingeniería de Sistemas")).to_be_visible()
    author.get_by_label("Buscar publicaciones").fill(title.upper())
    author.locator(".panel-categorias").get_by_role("button", name="Programación").click()
    card = article(author, title)
    expect(card).to_be_visible()
    author.get_by_label("Buscar publicaciones").fill("zzzaaa000ausente")
    expect(author.get_by_text("No encontramos preguntas con esos filtros.")).to_be_visible()
    author.get_by_label("Buscar publicaciones").fill(title.lower())
    card.get_by_role("button", name="Editar", exact=True).click()
    card.get_by_label("Contenido", exact=True).fill("Contenido académico corregido.")
    card.get_by_role("button", name="Guardar cambios").click()
    expect(card.get_by_text("Contenido académico corregido.")).to_be_visible()

    peer = browser.new_page()
    peer.goto(BASE)
    register(peer)
    other_card = article(peer, title)
    expect(other_card.get_by_role("button", name="Editar", exact=True)).to_have_count(0)
    open_replies(other_card)
    reply = "Referencia " + secrets.token_hex(5)
    other_card.get_by_label("Escribir una respuesta").fill(reply)
    other_card.get_by_role("button", name="Adjuntar libro de Google Books").click()
    other_card.get_by_label("Título o autor").fill("algebra")
    other_card.get_by_role("button", name="Buscar libros").click()
    other_card.get_by_role("button", name="Álgebra lineal", exact=False).click()
    other_card.get_by_role("button", name="Publicar comentario").click()
    link = other_card.get_by_role("link", name="Álgebra lineal")
    expect(link).to_have_attribute("href", "https://books.google.com/books?id=vol_1")
    for width in [360, 767, 768, 769]:
        check_width(peer, width)
        expect(other_card.get_by_text(reply, exact=True)).to_be_visible()

    if os.getenv("FORO_TEST_MOD_EMAIL"):
        reason = "Revisar " + secrets.token_hex(6)
        other_card.locator(".acciones-aporte").first.get_by_role("button", name="Reportar").click()
        other_card.get_by_label("Motivo del reporte").fill(reason)
        other_card.get_by_role("button", name="Enviar reporte").click()
        expect(other_card.get_by_text("Reporte enviado a moderación.")).to_be_visible()
        mod = browser.new_page()
        mod.goto(BASE)
        login(mod, os.environ["FORO_TEST_MOD_EMAIL"], os.environ["FORO_TEST_MOD_PASSWORD"])
        mod.get_by_role("button", name="Reportes pendientes").click()
        report = mod.locator("article").filter(has_text=reason)
        report.get_by_label("Motivo de la decisión").fill("El contenido cumple las normas.")
        report.get_by_role("button", name="Desestimar reporte").click()
        expect(report).to_have_count(0)
        mod.close()

    author.reload()
    own_card = article(author, title)
    own_card.get_by_role("button", name="Retirar", exact=True).click()
    own_card.get_by_role("button", name="Confirmar retiro").click()
    expect(own_card).to_have_count(0)
    peer.reload()
    expect(peer.get_by_role("button", name="Mi perfil")).to_be_visible()
    expect(article(peer, "Consulta retirada").first).to_be_visible()
    # Other tests may also have left a withdrawn thread. Identify this one by its reply.
    found = False
    for withdrawn in article(peer, "Consulta retirada").all():
        open_replies(withdrawn)
        withdrawn.locator(".comentarios-lista").wait_for()
        if withdrawn.get_by_text(reply, exact=True).count():
            found = True
            expect(withdrawn.get_by_role("link", name="Álgebra lineal")).to_be_visible()
            withdrawn.locator(".comentario-tarjeta").filter(has_text=reply).get_by_role("button", name="Retirar").click()
            withdrawn.get_by_role("button", name="Confirmar retiro").click()
            expect(peer.get_by_text(reply, exact=True)).to_have_count(0)
            break
    assert found, "La respuesta de la segunda cuenta debe conservarse"
    assert not errors, errors
    print("Chromium: perfil, publicación, búsqueda, filtro, autoría, reporte, retiro y libro citado.")
    print("Cinco anchos revisados: 360, 767, 768, 769 y 1280 px.")


if __name__ == "__main__":
    with sync_playwright() as pw:
        browser = pw.chromium.launch(executable_path=os.getenv("CHROMIUM_PATH"))
        try:
            run(browser)
        finally:
            browser.close()
