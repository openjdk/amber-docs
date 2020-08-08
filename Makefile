OUT_DIR = out
IN_DIR = site
CSS = style.css

WILDCARD = $(patsubst $(IN_DIR)/%, %, $(wildcard $(IN_DIR)/*.$(1) $(IN_DIR)/**/*.$(1) ))

ASSET_EXTS = html jpg jpg gif svg

MD_SOURCES = $(call WILDCARD,md)
ASSET_SOURCES = $(foreach X, $(ASSET_EXTS), $(call WILDCARD,$(X)))

GENERATED_FILES = $(patsubst %.md, $(OUT_DIR)/%.html, $(MD_SOURCES))
COPIED_FILES = $(patsubst %,$(OUT_DIR)/%, $(ASSET_SOURCES))

site: $(GENERATED_FILES) $(COPIED_FILES)

$(GENERATED_FILES) : $(OUT_DIR)/%.html : $(IN_DIR)/%.md
	mkdir -p $(dir $@)
	pandoc -f markdown $< -o $@ -H $(CSS)

$(COPIED_FILES) : $(OUT_DIR)/% : $(IN_DIR)/%
	mkdir -p $(dir $@)
	cp $< $@

clean:
	rm -rf $(OUT_DIR)

.PHONY: all clean
