package org.eln2.serialization.generic

/*
NOTE: This code is strictly experimental
 */

class JsonSerializer : ISerialize, ISerializeLazily {

	private var backingJson = ""

	private var backingData = mutableMapOf<String, Any>()

	constructor()
	constructor(json: String) {
		backingJson = combineJsonProperties(backingJson, json)
	}

	override var isLazy = false

	override fun getInt(key: String): Int? {
		if (isLazy) {
			return 0
		} else {
			return 0
		}
	}

	override fun getBool(key: String): Boolean? {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun getDouble(key: String): Double? {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun getString(key: String): String? {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun setInt(key: String, value: Int) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun setBool(key: String, value: Boolean) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun setDouble(key: String, value: Double) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun setString(key: String, value: String) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun getNested(key: String): ISerialize? {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun setNested(key: String, value: ISerialize) {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun commit() {
		if (isLazy) {
			// TODO: Code to commit dict to json
			// TODO: Code to load from json again
		}
	}

	companion object {
		fun combineJsonProperties(json1: String, json2: String): String {
			return ""
		}
	}
}
