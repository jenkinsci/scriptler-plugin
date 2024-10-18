document.addEventListener("DOMContentLoaded", () => {
  const removeScriptButtons = document.querySelectorAll(".scriptler-remove-script-form");
  removeScriptButtons.forEach((button) =>
    button.addEventListener("click", (e) => {
      const name = e.currentTarget.getAttribute("data-name");
      if (!confirm("Sure you want to delete [" + name + "]?")) {
        e.preventDefault();
      }
    }),
  );
});
