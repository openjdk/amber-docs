OUT_DIR = web
IN_DIR = site
CSS = etc/style.css

IN_DIR_FILES := $(shell find $(IN_DIR) -type f)
WILDCARD = $(patsubst $(IN_DIR)/%, %, $(filter %.$(1), $(IN_DIR_FILES)))

ASSET_EXTS = html jpg jpg gif svg png pdf

MD_SOURCES = $(call WILDCARD,md)
ASSET_SOURCES = $(foreach X, $(ASSET_EXTS), $(call WILDCARD,$(X)))

GENERATED_FILES = $(patsubst %.md, $(OUT_DIR)/%.html, $(MD_SOURCES))
COPIED_FILES = $(filter-out $(GENERATED_FILES), $(patsubst %,$(OUT_DIR)/%, $(ASSET_SOURCES)))
AUTO_SITE_MAP = $(OUT_DIR)/README.html

site: $(GENERATED_FILES) $(COPIED_FILES) $(AUTO_SITE_MAP)
	tar czf site.tar.gz -C $(OUT_DIR) `cd $(OUT_DIR); ls`
# (do not use tar --xform; some platforms do not have it)

$(GENERATED_FILES) : $(OUT_DIR)/%.html : $(IN_DIR)/%.md
	@mkdir -p "$(@D)"
	pandoc -f markdown "$<" -o "$@" $(call PANDOC_OPTIONS, "$<")

$(COPIED_FILES) : $(OUT_DIR)/% : $(IN_DIR)/%
	@mkdir -p "$(@D)"
	cp "$<" "$@"

# Tweak the options to pandoc.
PANDOC_OPTIONS = \
	--standalone \
	--include-after-body=etc/footer.html \
	$(if $(call HAS_STYLE, $(1)),,-H "$(CSS)") \
	$(if $(call HAS_PAGETITLE, $(1)),,--metadata pagetitle="$(basename $(notdir $(1)))") \
	$(call HAS_PANDOC_FLAGS, $(1))

# Specify -H $(CSS) if we do not already have embedded style.
HAS_STYLE = $(shell sed -n < $(1) '/^ *<style[ >]/{p;q;};999q')
# Avoid pandoc warning by specifying a default page title.
HAS_PAGETITLE = $(shell sed -n < $(1) '1s/^% /<title>/;/^ *<title[ >]/{p;q;};999q')
# Allow document to embed misc. pandoc flags, such as <meta pandoc-flags="--toc">.
HAS_PANDOC_FLAGS = $(shell sed -n < $(1) '/^<meta pandoc-flags="\(.*\)">/s//\1/p;999q')


$(AUTO_SITE_MAP) : $(GENERATED_FILES) $(COPIED_FILES) Makefile
	( cd $(OUT_DIR); \
	  echo "<h3>Site map:</h3><ul>"; \
	  find * -type f -name \*.html ! -name README.html \
	  | sort | sed 's|.*|<li><a href="&">&</a></li>|'; \
	  echo "</ul>"; \
	) > "$@"

clean:
	rm -rf $(OUT_DIR)

.PHONY: all site clean
