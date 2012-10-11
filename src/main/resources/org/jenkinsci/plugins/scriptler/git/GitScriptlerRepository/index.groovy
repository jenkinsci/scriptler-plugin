def l=namespace(lib.LayoutTagLib)

l.layout {
    l.main_panel {
        h1 "Accessing UserContent"

        p {
            raw _("blurb",app.rootUrl)
        }
        pre {
            def url = "${app.rootUrl}scriptler.git"
            raw "git clone <a href='${url}'>${url}</a>"

            if (my.sshd.actualPort>0) {
                raw "\ngit clone ssh://${new URL(app.rootUrl).host}:${my.sshd.actualPort}/userContent.git"
            }
        }
    }
}
