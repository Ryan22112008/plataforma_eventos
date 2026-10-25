/* ==========================================================================
   SEPLAG Eventos — shell v2
   Controla apenas o drawer de navegação em telas menores.
   Sem dependências, sem build. Não interfere em formulários ou links.
   ========================================================================== */
(function () {
    'use strict';

    var body = document.body;
    var toggle = document.querySelector('[data-seplag-menu-toggle]');
    var backdrop = document.querySelector('[data-seplag-backdrop]');
    var sidebar = document.getElementById('seplag-sidebar');

    if (!toggle || !sidebar) {
        return;
    }

    var DESKTOP = 1024;
    var isOpen = false;

    function setOpen(open, returnFocus) {
        isOpen = open;
        body.classList.toggle('seplag-shell--open', open);
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        toggle.setAttribute('aria-label', open ? 'Fechar menu de navegação' : 'Abrir menu de navegação');

        if (open) {
            var first = sidebar.querySelector('a, button');
            if (first) {
                first.focus();
            }
        } else if (returnFocus) {
            toggle.focus();
        }
    }

    toggle.addEventListener('click', function () {
        setOpen(!isOpen, false);
    });

    if (backdrop) {
        backdrop.addEventListener('click', function () {
            setOpen(false, true);
        });
    }

    sidebar.addEventListener('click', function (event) {
        var link = event.target.closest('a');
        if (link && window.innerWidth < DESKTOP) {
            setOpen(false, false);
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && isOpen) {
            setOpen(false, true);
        }
    });

    window.addEventListener('resize', function () {
        if (window.innerWidth >= DESKTOP && isOpen) {
            setOpen(false, false);
        }
    });
})();
