# cb-ext-config-service

Form Configuration Service for iGOT Karmayogi (Spring Boot). Stores and serves form configurations via V1 and V2 REST APIs.

## Documentation

Published at **https://kb-igot.github.io/cb-ext-config-service/** from the `docs/` folder.

- `docs/index.md` — landing page
- `docs/API_DOCUMENTATION.md` — API reference
- `docs/swagger.html` — Swagger UI rendering `docs/openapi.json`
- `docs/form_configuration_apis_postman_collection.json` — Postman collection

### Enabling GitHub Pages (one-time)

1. GitHub repo → **Settings → Pages**
2. **Source**: Deploy from a branch
3. **Branch**: `cbrelease-4.8.39.2`, **Folder**: `/docs` → Save
4. The site builds with Jekyll using `docs/_config.yml`; no CI workflow is needed. Allow a minute or two for the first build.

When the release branch changes, update the branch in step 3.

### Previewing locally

```bash
cd docs
gem install bundler jekyll
jekyll serve --baseurl /cb-ext-config-service
# open http://localhost:4000/cb-ext-config-service/
```
