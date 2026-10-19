.DEFAULT_GOAL := test

.PHONY: test build lint clean

test:
	./gradlew testDebugUnitTest --offline

build:
	./gradlew assembleDebug --offline

lint:
	./gradlew lintDebug --offline

clean:
	./gradlew clean --offline
