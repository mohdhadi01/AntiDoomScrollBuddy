.DEFAULT_GOAL := test

.PHONY: test build lint clean assets check-assets

test: check-assets
	./gradlew testDebugUnitTest --offline

assets:
	python3 tools/generate_characters.py

check-assets:
	python3 tools/generate_characters.py --check

build:
	./gradlew assembleDebug --offline

lint:
	./gradlew lintDebug --offline

clean:
	./gradlew clean --offline
