BRANCH=$1

if [ -z "$BRANCH" ]; then
		BRANCH="forge_1.12.2"
fi
rm -rf TrackAPI
git clone --branch $BRANCH git@github.com:TeamOpenIndustry/TrackAPI.git
./gradlew clean
./gradlew cleanIdea
./gradlew idea
