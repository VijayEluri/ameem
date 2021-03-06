#Model of Return value definition CSV file

class ReturnValueDefinition
  attr_reader :values
  def initialize
    @values=[]
  end
  # Load from a return_values.csv file
  def self.from_file(filename)
    instance=ReturnValueDefinition.new
    instance.parse_csv(filename)
    instance
  end
  # Save to a return_values.csv file
  def self.from_amee_model(rvd)
    instance=ReturnValueDefinition.new
    instance.from_amee_model(rvd)
    instance
  end
  # Save to a return_values.csv file
  def to_file(filename)
    CSV.open(filename,'w') do |w|
      w << %w{label name type unit per_unit default}
      values.each do |value|
        value.write_csv(w)
      end
    end
  end
  # Load from a return_values.csv file
  def parse_csv(filename)
    @file=CSV.read(filename)
    # Read order of headers
    headers = @file.shift
    @file.each do |line|
      next if line.length==0 || (line.length==1 && line[0]==nil)
      # Read fields from line based on headers
      value=ReturnValue.new( line[headers.index('label')],
                             line[headers.index('name') || headers.index('label')],
                             line[headers.index('type')],
                             line[headers.index('unit')],
                             line[headers.index('per_unit')],
                             line[headers.index('default')] )
      @values.push value
    end
    validate
  end

  #Convert from AMEE Rubygem model
  #rvd is an AMEE::Admin::ReturnValueDefinitionList
  def from_amee_model(rvd)
    rvd.each do |avalue|
      vt=case avalue.valuetype
      when "CCEB59CACE1B"
        'TEXT'
      when "45433E48B39F"
        'DECIMAL'
      end
      value=ReturnValue.new(avalue.type,avalue.type,vt,avalue.unit,avalue.perunit) # Type used twice until AMEE 
                                                                                   # gem supports RVD names
      @values.push value
    end
    validate
  end
  def validate
    @values.select{|x|x.default}.length<=1 or
      raise "Too many defaults in return value definition, #{@values.inspect} "
    # removed this list of valid
#    @values.each{|x|
#      allowed=%w{CO2 CO2e CH4 N2O comment}
#      allowed.include? x.label or
#        raise "#{x.label} not a valid return value type, choose from #{allowed}"
#    }
  end
end

# A particular return value in an RVD file
class ReturnValue
  attr_reader :label,:name,:type,:unit,:perunit,:default
  def initialize(label,name,type,unit,perunit,default=false)
    @label=label
    @name=name
    @type=type
    @unit=unit
    @perunit=perunit
    @default=default.to_s.downcase=='true'
  end
  def write_csv(writer)
    writer << [label,name,type,unit,perunit,default]
  end
  def inspect
    [label,name,type,unit,perunit,default].inspect
  end
end
